package com.blckswan.blckchat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragments of ALREADY-ENCRYPTED SMS messages. Java has no crypto here:
 * a completed wire is always checked by NativeSmsCodec + AES-GCM before
 * any plaintext is displayed. No telephony permission or Android API used.
 *
 * A bounded, serializable collection supports arbitrary SMS order and
 * process restarts. The state contains ciphertext-only fragments.
 */
public final class SmsFrameAssembler {
    public static final int MAX_PENDING = 8;
    public static final int MAX_PARTS = 24;
    public static final int MAX_BODY_CHARS = 96;
    public static final int MAX_SMS_CHARS = 153;
    public static final long TTL_MILLIS = 48L * 60L * 60L * 1000L;
    private static final int MAX_SNAPSHOT = 45000;

    public enum State { PENDING, COMPLETE }

    public static final class Result {
        public final State state;
        public final String id;
        public final int received;
        public final int expected;
        public final String completeWire;

        private Result(State state, String id, int received,
                       int expected, String completeWire) {
            this.state=state;
            this.id=id;
            this.received=received;
            this.expected=expected;
            this.completeWire=completeWire;
        }
    }

    private static final class Frame {
        final String id;
        final int part, count;
        final String body;
        Frame(String id, int part, int count, String body) {
            this.id=id;this.part=part;this.count=count;this.body=body;
        }
    }

    private static final class Entry {
        final String id;
        final int count;
        final long created;
        final String[] fragments;
        int received;
        Entry(String id, int count, long created) {
            this.id=id;this.count=count;this.created=created;
            fragments=new String[count];
        }
        String complete() {
            if(received!=count) return null;
            StringBuilder result=new StringBuilder(count*145);
            for(int i=0;i<count;i++) {
                if(fragments[i]==null)return null;
                if(i>0)result.append('\n');
                result.append(asLine(id,i+1,count,fragments[i]));
            }
            return result.toString();
        }
    }

    private final LinkedHashMap<String,Entry> pending = new LinkedHashMap<>();

    private static String two(int x) {
        return x<10?"0"+x:Integer.toString(x);
    }

    private static String asLine(String id,int part,int count,String payload) {
        return "BC42|1|"+id+"|"+two(part)+"/"+two(count)+"|"+payload;
    }

    private static boolean lowerHex(char c) {
        return (c>='0'&&c<='9')||(c>='a'&&c<='f');
    }

    private static boolean base64url(char c) {
        return (c>='A'&&c<='Z')||(c>='a'&&c<='z')||
               (c>='0'&&c<='9')||c=='-'||c=='_';
    }

    private static int decimalPair(String text,int start) {
        char a=text.charAt(start),b=text.charAt(start+1);
        if(a<'0'||a>'9'||b<'0'||b>'9')
            throw new IllegalArgumentException("Ugyldig SMS-delnummer");
        return (a-'0')*10+(b-'0');
    }

    private static Frame parse(String text) {
        if(text==null||text.length()<47||text.length()>MAX_SMS_CHARS||
           !text.startsWith("BC42|1|")||text.charAt(39)!='|'||
           text.charAt(42)!='/'||text.charAt(45)!='|')
            throw new IllegalArgumentException("Ugyldig BC42 SMS-format");
        for(int i=7;i<39;i++)if(!lowerHex(text.charAt(i)))
            throw new IllegalArgumentException("Ugyldig meldings-ID");
        String id=text.substring(7,39);
        int part=decimalPair(text,40),count=decimalPair(text,43);
        if(count<1||count>MAX_PARTS||part<1||part>count)
            throw new IllegalArgumentException("Ugyldig antall SMS-deler");
        String payload=text.substring(46);
        if(payload.isEmpty()||payload.length()>MAX_BODY_CHARS||
           (part<count&&payload.length()!=MAX_BODY_CHARS))
            throw new IllegalArgumentException("Ugyldig SMS-delstørrelse");
        for(int i=0;i<payload.length();i++)if(!base64url(payload.charAt(i)))
            throw new IllegalArgumentException("Ugyldige tegn i kryptert SMS");
        return new Frame(id,part,count,payload);
    }

    private void prune(long now) {
        Iterator<Entry> iterator=pending.values().iterator();
        while(iterator.hasNext()) {
            Entry e=iterator.next();
            if(now<e.created||now-e.created>TTL_MILLIS)
                iterator.remove();
        }
    }

    /**
     * Accept one SMS frame, several newline-separated frames, or exact
     * duplicates. Reject conflicting duplicates or spliced message IDs.
     * Result COMPLETE is *not authenticated*. Decrypt and verify separately.
     */
    public synchronized Result accept(String raw,long now) {
        if(raw==null||raw.length()>MAX_PARTS*(MAX_SMS_CHARS+1)||
           raw.isEmpty()||now<0)
            throw new IllegalArgumentException("Ugyldig SMS-tekst");
        String source=raw.trim();
        String[] lines=source.split("\\r?\\n",-1);
        if(lines.length==0||lines.length>MAX_PARTS)
            throw new IllegalArgumentException("For mange SMS-deler");
        List<Frame> frames=new ArrayList<>(lines.length);
        String id=null;
        for(String line:lines) {
            Frame f=parse(line);
            if(id!=null&&!id.equals(f.id))
                throw new IllegalArgumentException("SMS-deler fra ulike meldinger");
            id=f.id;
            frames.add(f);
        }
        prune(now);
        Entry e=pending.get(id);
        if(e==null) {
            if(pending.size()>=MAX_PENDING)
                throw new IllegalStateException("Lagringskøen er full. Slett gamle deler.");
            e=new Entry(id,frames.get(0).count,now);
        }
        // Validate ALL frames and conflicts before committing anything.
        String[] staged=Arrays.copyOf(e.fragments,e.fragments.length);
        for(Frame f:frames) {
            if(f.count!=e.count)
                throw new IllegalArgumentException("Motstridende SMS-antall");
            String existing=staged[f.part-1];
            if(existing!=null&&!existing.equals(f.body))
                throw new IllegalArgumentException("Konflikt mellom SMS-deler");
            staged[f.part-1]=f.body;
        }
        System.arraycopy(staged,0,e.fragments,0,staged.length);
        e.received=0;
        for(String p:e.fragments)if(p!=null)e.received++;
        pending.put(id,e);
        String full=e.complete();
        return new Result(full==null?State.PENDING:State.COMPLETE,
                          id,e.received,e.count,full);
    }

    public synchronized void discard(String id) {
        if(id!=null)pending.remove(id);
    }

    public synchronized void clear() {
        pending.clear();
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    /** Ciphertext-only snapshot for SharedPreferences (no keys/plaintext). */
    public synchronized String snapshot(long now) {
        prune(now);
        StringBuilder s=new StringBuilder();
        for(Entry e:pending.values()) {
            for(int i=0;i<e.count;i++) {
                if(e.fragments[i]==null)continue;
                s.append(e.id).append('|').append(e.count).append('|')
                 .append(e.created).append('|').append(i+1).append('|')
                 .append(e.fragments[i]).append('\n');
                if(s.length()>MAX_SNAPSHOT)
                    throw new IllegalStateException("SMS-cache over grensen");
            }
        }
        return s.toString();
    }

    /** Ignore untrusted/corrupt/stale cache rows; no user data decrypted. */
    public synchronized void restore(String snapshot,long now) {
        pending.clear();
        if(snapshot==null||snapshot.length()>MAX_SNAPSHOT||now<0)return;
        for(String line:snapshot.split("\\n",-1)) {
            if(line.isEmpty())continue;
            String[] fields=line.split("\\|",5);
            if(fields.length!=5)continue;
            try {
                String id=fields[0];
                int total=Integer.parseInt(fields[1]);
                long created=Long.parseLong(fields[2]);
                int part=Integer.parseInt(fields[3]);
                if(created<0||created>now||now-created>TTL_MILLIS||
                   total<1||total>MAX_PARTS||part<1||part>total)continue;
                Frame f=parse(asLine(id,part,total,fields[4]));
                Entry e=pending.get(id);
                if(e==null) {
                    if(pending.size()>=MAX_PENDING)break;
                    e=new Entry(id,total,created);
                    pending.put(id,e);
                }
                if(e.count!=total||e.created!=created)continue;
                String existing=e.fragments[part-1];
                if(existing!=null&&!existing.equals(f.body))continue;
                if(existing==null) {
                    e.fragments[part-1]=f.body;
                    e.received++;
                }
            }catch(RuntimeException ignored) {
                // A corrupted local ciphertext cache must not crash the app.
            }
        }
        prune(now);
    }
}
