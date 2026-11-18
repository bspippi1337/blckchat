package com.blckswan.blckchat;

public final class SmsFrameAssemblerTest {
    private static final String ID="0123456789abcdef0123456789abcdef";
    private static final String OTHER="fedcba9876543210fedcba9876543210";
    private static void yes(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }
    private static String frame(String id,int part,int count,String body) {
        return "BC42|1|"+id+"|"+(part<10?"0":"")+part+"/"
             +(count<10?"0":"")+count+"|"+body;
    }
    private static void reject(Runnable fn,String reason){
        boolean rejected=false;
        try{fn.run();}catch(IllegalArgumentException|IllegalStateException ex){
            rejected=true;
        }
        yes(rejected,reason);
    }
    public static void main(String[] args) {
        SmsFrameAssembler a=new SmsFrameAssembler();
        String first=frame(ID,1,2,"A".repeat(96));
        String second=frame(ID,2,2,"AgM");
        String other=frame(OTHER,1,1,"BAU");
        long t=10_000L;
        SmsFrameAssembler.Result r=a.accept(second,t);
        yes(r.state==SmsFrameAssembler.State.PENDING,"second fragment first");
        yes(r.received==1&&r.expected==2,"fragment progress");
        yes(a.accept(second,t).received==1,"idempotent exact duplicate");
        String stored=a.snapshot(t);
        yes(!stored.contains("plaintext")&&stored.contains(ID),"ciphertext snapshot");
        SmsFrameAssembler restarted=new SmsFrameAssembler();
        restarted.restore(stored,t+1000);
        r=restarted.accept(first,t+1200);
        yes(r.state==SmsFrameAssembler.State.COMPLETE,"reordered completion");
        yes(r.completeWire.equals(first+"\n"+second),"canonical order");
        yes(restarted.accept(first,t+1200).state==
            SmsFrameAssembler.State.COMPLETE,"duplicate after completion");

        reject(()->restarted.accept(frame(ID,1,2,"B".repeat(96)),t+1200),
               "conflicting duplicate");
        reject(()->restarted.accept(other+"\n"+first,t+1200),
               "cross-message splice");
        reject(()->restarted.accept(first.replace("BC42","BC43"),t+1200),
               "wrong version");
        reject(()->restarted.accept(first.replace('|','.'),t+1200),
               "corrupt delimiters");
        reject(()->restarted.accept(frame(ID,1,2,"a"),t+1200),
               "short nonfinal segment");
        reject(()->restarted.accept(frame(ID,1,25,"A".repeat(96)),t+1200),
               "oversized count");
        reject(()->restarted.accept(frame(ID,3,2,"AgM"),t+1200),
               "index exceeds count");
        reject(()->restarted.accept(frame(ID,1,1,"./"),t+1200),
               "invalid base64url");
        reject(()->restarted.accept(first+"\n"+other,t+1200),
               "mixed identifiers");
        yes(restarted.pendingCount()==1,"invalid input is atomic");

        restarted.discard(ID);
        yes(restarted.pendingCount()==0,"acknowledged message removed");
        yes(restarted.accept(other,t+1500).state==
            SmsFrameAssembler.State.COMPLETE,"single segment supported");
        restarted.restore(stored,t+SmsFrameAssembler.TTL_MILLIS+1);
        yes(restarted.pendingCount()==0,"stale ciphertext discarded");
        restarted.restore(stored,t-1);
        yes(restarted.pendingCount()==0,"future-dated cache rejected");
        restarted.restore("bad|nonsense\n",t);
        yes(restarted.pendingCount()==0,"corrupt snapshot ignored");
        for(int i=0;i<SmsFrameAssembler.MAX_PENDING;i++){
            String different=String.format("%032x",i+1);
            restarted.accept(frame(different,1,2,"A".repeat(96)),t);
        }
        yes(restarted.pendingCount()==SmsFrameAssembler.MAX_PENDING,"bounded queue");
        reject(()->restarted.accept(first,t),"no unbounded memory growth");
        restarted.clear();
        yes(restarted.pendingCount()==0,"clear");
        System.out.println("PASS SMS Java assembly: order, duplicate, restart, TTL, bounded queue and validation");
    }
}
