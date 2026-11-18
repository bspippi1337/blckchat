package com.blckswan.blckchat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;

/**
 * No-Wi-Fi SMS-first Android alpha. Uses C11 transport framing; device
 * identity/ECDH/AEAD delegated to Android's reviewed security providers.
 * DOES NOT use SEND_SMS, READ_SMS, internet, or a local LAN server.
 * SMS messages are composed with Android's system SMS app. Incoming
 * messages are imported explicitly via paste or ACTION_SEND share text.
 */
public final class RemoteSmsActivity extends Activity {
    static { System.loadLibrary("blcknative"); }
    private static native int nativeAccent();
    private static final int BG=0xff090e15;
    private static final int PANEL=0xff131f2a;
    private static final int RAISED=0xff1a2b35;
    private static final int FG=0xffebf5f4;
    private static final int SUB=0xffadc2c5;
    private static final int BORDER=0xff354954;
    private int accent;
    private SmsE2ee crypto;
    private SmsFrameInbox inbox;
    private SmsDraftStore drafts;
    private LinearLayout body;
    private EditText phone,peerCode,outgoing,incoming;
    private TextView status,fingerprint,decrypted;
    private CheckBox verified;
    private TextView sendButton;
    private TextView outboxProgress;
    private LinearLayout pairingPanel;

    private int dp(int value){ return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable panel(int color,int radius){
        GradientDrawable d=bg(color,radius);d.setStroke(dp(1),BORDER);return d;
    }
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(color);
        t.setTextSize(sp);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout col(){
        LinearLayout result=new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);return result;
    }
    private LinearLayout.LayoutParams match(){
        return new LinearLayout.LayoutParams(-1,-2);
    }
    private void gap(View child,int top){
        LinearLayout.LayoutParams p=match();p.topMargin=dp(top);
        body.addView(child,p);
    }
    private void paragraph(String message,int top){
        TextView t=label(message,14,SUB,false);
        t.setLineSpacing(dp(3),1.04f);gap(t,top);
    }
    private TextView action(String title,boolean bright,View.OnClickListener call){
        TextView t=label(title,15,bright?BG:FG,true);
        t.setGravity(Gravity.CENTER);t.setMinHeight(dp(54));
        t.setPadding(dp(16),dp(12),dp(16),dp(12));
        t.setBackground(bg(bright?accent:RAISED,13));
        t.setOnClickListener(call);t.setFocusable(true);return t;
    }
    private EditText input(String title,String hint,boolean multiline){
        TextView t=label(title,12,accent,true);
        t.setLetterSpacing(.10f);gap(t,20);
        EditText e=new EditText(this);
        e.setTextSize(15);e.setTextColor(FG);e.setHintTextColor(SUB);
        e.setHint(hint);e.setMinHeight(dp(54));
        e.setPadding(dp(14),dp(11),dp(14),dp(11));
        e.setBackground(panel(RAISED,12));
        e.setSingleLine(!multiline);
        if(multiline){e.setMinLines(2);e.setMaxLines(6);
            e.setGravity(Gravity.TOP|Gravity.START);}
        gap(e,7);return e;
    }
    private void status(String msg){if(status!=null)status.setText(msg);}
    private void showFailure(Exception ex){
        String m=ex.getMessage();
        status(m==null?"Handlingen mislyktes":m);
        new AlertDialog.Builder(this)
            .setTitle("BLCKCHAT")
            .setMessage(m==null?"Handlingen mislyktes":m)
            .setPositiveButton("OK",null).show();
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        accent=nativeAccent();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        try{
            crypto=new SmsE2ee(this);
            inbox=new SmsFrameInbox(this);
            drafts=new SmsDraftStore(this);
        }catch(Exception ex){
            showUnsupported(ex);return;
        }
        render();
        receiveIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        receiveIntent(intent);
    }
    private void showUnsupported(Exception ex){
        TextView t=label("BLCKCHAT 42\n\nSMS-kryptering kunne ikke startes. "
            +"Android 12+ med tilgjengelig sikker nøkkellagring er påkrevd.\n\n"
            +ex.getClass().getSimpleName()+": "+ex.getMessage(),17,FG,false);
        t.setPadding(dp(25),dp(35),dp(25),dp(25));
        t.setBackgroundColor(BG);setContentView(t);
    }
    private void render(){
        ScrollView outer=new ScrollView(this);
        outer.setFillViewport(true);outer.setBackgroundColor(BG);
        body=col();body.setPadding(dp(20),dp(25),dp(20),dp(40));
        outer.addView(body);setContentView(outer);
        TextView eyebrow=label("BLCK / CHAT     42 · SMS FIRST",12,accent,true);
        eyebrow.setLetterSpacing(.16f);body.addView(eyebrow,match());
        TextView title=label("Avstand er ikke et hinder.",31,FG,true);
        gap(title,16);
        paragraph("Ekte krypterte meldinger over operatørens SMS-nett. "
            +"Ingen felles Wi-Fi, GitHub-innlogging eller server.",13);
        status=label("Nøkkelen din lagres lokalt i Android Keystore.",12,accent,false);
        status.setPadding(dp(14),dp(13),dp(14),dp(13));
        status.setBackground(panel(PANEL,12));gap(status,18);
        gap(action("KJØR KRYPTO-SELVTEST PÅ DENNE TELEFONEN",false,
            v->selfTest()),10);
        paragraph("TESTVERSJON: Begge trenger Android 12+. Dette er en eksperimentell "
            +"parvis kryptert SMS-chat, ikke sikkerhetsrevidert eller Signal-ekvivalent. "
            +"SMS-operatøren ser hvem som sender til hvem.",15);

        TextView pairing=label("01 / KOBLE SAMMEN TO TELEFONER",18,FG,true);
        gap(pairing,30);
        phone=input("MOTTAKERS MOBILNUMMER","+47…",false);
        gap(action("SEND MIN OFFENTLIGE NØKKEL VIA SMS",true,v->sendKey()),14);
        paragraph("Mottakeren åpner SMS-en, kopierer hele BC42K-koden og "
            +"limer den inn i BLCKCHAT. Deretter sender mottakeren sin egen kode tilbake.",10);
        peerCode=input("LIM INN MOTTAKERS BC42K-KODE","BC42K|1|…",true);
        gap(action("IMPORTER OFFENTLIG NØKKEL",false,v->importKey()),12);

        pairingPanel=col();
        pairingPanel.setPadding(dp(16),dp(18),dp(16),dp(18));
        pairingPanel.setBackground(panel(PANEL,14));
        fingerprint=label("Sikkerhetskode:",16,FG,true);pairingPanel.addView(fingerprint,match());
        TextView help=label("Sammenlign koden muntlig over en separat, "
            +"pålitelig samtale. Begge telefonene må vise nøyaktig samme kode. "
            +"Ikke stol på en kode kun mottatt i SMS.",13,SUB,false);
        LinearLayout.LayoutParams hp=match();hp.topMargin=dp(10);
        pairingPanel.addView(help,hp);
        verified=new CheckBox(this);
        verified.setText("Jeg har sammenlignet og bekreftet koden");
        verified.setTextColor(FG);
        verified.setTextSize(14);verified.setButtonTintList(
            android.content.res.ColorStateList.valueOf(accent));
        verified.setOnCheckedChangeListener((b,checked)->{
            if(crypto!=null && crypto.hasPeer()){
                try{crypto.setVerified(checked);
                    status(checked?"Identiteten er bekreftet. Meldinger kan krypteres.":
                          "Identiteten må verifiseres før sending.");
                    sendButton.setEnabled(checked);
                    sendButton.setAlpha(checked?1f:.55f);
                }catch(Exception e){showFailure(e);}
            }
        });
        LinearLayout.LayoutParams vp=match();vp.topMargin=dp(12);
        pairingPanel.addView(verified,vp);
        TextView reset=action("FJERN PARING",false,v->askForget());
        LinearLayout.LayoutParams rp=match();rp.topMargin=dp(16);
        pairingPanel.addView(reset,rp);
        gap(pairingPanel,16);

        TextView compose=label("02 / SKRIV EN KRYPTERT SMS",18,FG,true);
        gap(compose,34);
        outgoing=input("MELDING","Skriv en kort melding…",true);
        gap(action("SEND KRYPTERT SMS",true,v->sendEncrypted()),12);
        sendButton=(TextView)body.getChildAt(body.getChildCount()-1);
        gap(action("ÅPNE UTSENDING / NESTE SMS-DEL",false,v->retryDraft()),8);
        gap(action("BEKREFT DENNE SMS-DELEN",false,v->confirmDraft()),8);
        gap(action("FORKAST UTSENDING",false,v->discardDraft()),8);
        outboxProgress=label("",12,accent,true);
        gap(outboxProgress,10);
        updateOutboxProgress();
        paragraph("BLCKCHAT åpner én kryptert SMS-del om gangen. Bekreft manuelt "+
            "at akkurat den delen ble sendt før neste åpnes. Fremdriften "+
            "lagres også når appen starter på nytt.",10);
        paragraph("Telefonens vanlige SMS-app åpnes med bare kryptert tekst. "
            +"Du kontrollerer kostnadene og trykker Send selv. Lange meldinger "
            +"kan koste flere SMS-er.",10);
        TextView incomingLabel=label("03 / LES EN KRYPTERT SMS",18,FG,true);
        gap(incomingLabel,32);
        incoming=input("KOPIER HELE SMS-TEKSTEN INN HER","BC42|1|…",true);
        gap(action("DEKRYPTER / LEGG TIL SMS-DEL",false,v->decrypt()),12);
        gap(action("SLETT UFERDIGE SMS-DELER",false,v->clearIncomplete()),9);
        decrypted=label("Innkommende melding vises her etter godkjent autentisering.",
            15,SUB,false);
        decrypted.setPadding(dp(15),dp(15),dp(15),dp(15));
        decrypted.setBackground(panel(PANEL,12));gap(decrypted,16);
        paragraph("Hvis en melding kommer som flere SMS-er: lim inn én del av gangen. "
            +"Delene lagres kryptert, uansett rekkefølge, også ved omstart. "
            +"Tips: bruk Del-funksjonen i SMS-appen. Ingen SMS-tilgang kreves.",15);
        gap(action("KOPIER ANONYM TESTSTATUS",false,v->copyDiagnostic()),18);
        paragraph("Testrapporten inneholder ikke telefonnummer, meldinger, "+
            "private nøkler eller offentlige nøkkelkoder.",8);
        gap(action("BRUKSANVISNING OG KILDEKODE",false,v->{
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(
                "https://bspippi1337.github.io/blckchat/")));
        }),20);
        updatePairing();
    }
    private void selfTest(){
        status("Tester Android Keystore, nøkkelutveksling og AES-GCM lokalt…");
        Thread worker=new Thread(()->{
            try {
                crypto.runDeviceSelfTest();
                runOnUiThread(()->{
                    status("Selvtest bestått. Du kan utveksle offentlig nøkkel.");
                    new AlertDialog.Builder(this)
                        .setTitle("Lokal krypteringstest bestått")
                        .setMessage("Android Keystore, ECDH, HKDF og AES-GCM fungerer "+
                            "på denne telefonen. Dette bekrefter ikke en ekte "+
                            "melding til en annen telefon eller sikkerhetsrevisjon.")
                        .setPositiveButton("OK",null).show();
                });
            }catch(Exception e) {
                runOnUiThread(()->showFailure(e));
            }
        },"blck-crypto-selftest");
        worker.setDaemon(true);
        worker.start();
    }
    private void copyDiagnostic(){
        String version="unknown";
        try {
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(
                getPackageName(),0);
            if(info.versionName!=null)version=info.versionName;
        }catch(Exception ignored){}
        String report="BLCKCHAT 42 TESTSTATUS\\n"+
            "Versjon: "+version+"\\n"+
            "Android API: "+android.os.Build.VERSION.SDK_INT+"\\n"+
            "Keystore-identitet: "+(crypto!=null?"Opprettet":"Utilgjengelig")+"\\n"+
            "Mottaker registrert: "+(crypto!=null&&crypto.hasPeer()?"Ja":"Nei")+"\\n"+
            "Sikkerhetskode bekreftet: "+(crypto!=null&&crypto.verified()?"Ja":"Nei")+"\\n"+
            "Uferdige krypterte meldinger: "+(inbox!=null?inbox.pendingCount():0)+"\\n"+
            "Uavklart kryptert utkast: "+(drafts!=null&&drafts.pending()!=null?"Ja":"Nei")+"\\n"+
            "Kjøring: eksperimentell SMS-alpha, ikke sikkerhetsrevidert";
        android.content.ClipboardManager manager=
            (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        manager.setPrimaryClip(android.content.ClipData.newPlainText(
            "BLCKCHAT anonym teststatus",report));
        status("Anonym teststatus kopiert. Kan sendes til utvikleren.");
    }
    private void updatePairing(){
        if(crypto==null || pairingPanel==null)return;
        boolean ready=crypto.hasPeer();
        pairingPanel.setVisibility(ready?View.VISIBLE:View.GONE);
        if(ready){
            try{fingerprint.setText("SAMMENLIGN SIKKERHETSKODE\n"+crypto.safetyCode());}
            catch(Exception ex){showFailure(ex);}
            verified.setChecked(crypto.verified());
        }
        if(sendButton!=null){
            sendButton.setEnabled(crypto.verified());
            sendButton.setAlpha(crypto.verified()?1f:.55f);
        }
    }
    private String phone(){
        String p=phone.getText().toString().trim();
        if(!p.matches("\\+[1-9][0-9]{7,14}"))
            throw new IllegalArgumentException("Skriv nummer med landskode, f.eks. +47…");
        return p;
    }
    private void composeSms(String message){
        Intent sms=new Intent(Intent.ACTION_SENDTO);
        sms.setData(Uri.parse("smsto:"+Uri.encode(phone())));
        sms.putExtra("sms_body",message);
        try {
            startActivity(sms);
        }catch(android.content.ActivityNotFoundException ex){
            throw new IllegalStateException("Ingen SMS-app er tilgjengelig",ex);
        }
    }
    private void sendKey(){
        try{
            String publicCode=crypto.myPairingCode();
            String publicOnly=publicCode.substring("BC42K|1|".length());
            String joinUrl="https://bspippi1337.github.io/blckchat/join.html#k="+publicOnly;
            String sms="BLCKCHAT 42: Du er invitert til kryptert SMS-test. "+
                "1) Åpne lenken og installer appen. "+
                "2) Åpne samme lenke igjen og trykk Åpne BLCKCHAT. "+
                "Offentlig nøkkel legges inn automatisk. "+
                "Bekreft sikkerhetskoden ved å ringe hverandre. "+joinUrl;
            composeSms(sms);
            status("SMS-appen er åpnet med invitasjonslenke. Trykk Send der.");
        }catch(Exception ex){showFailure(ex);}
    }
    private void importKey(){
        try{
            String raw=peerCode.getText().toString().trim();
            int at=raw.indexOf("BC42K|1|");
            if(at>0)raw=raw.substring(at).split("\\s",2)[0];
            crypto.importPairingCode(raw);
            status("Nøkkelen er importert. Ring mottakeren og sammenlign sikkerhetskoden.");
            updatePairing();
        }catch(Exception ex){showFailure(ex);}
    }
    private void sendEncrypted(){
        try{
            phone(); // Validate destination before storing an encrypted draft.
            if(drafts.pending()!=null)
                throw new IllegalStateException(
                    "Først må du fullføre eller forkaste tidligere kryptert SMS.");
            String plain=outgoing.getText().toString().trim();
            String wire=crypto.encrypt(plain);
            drafts.remember(wire);
            outgoing.setText("");
            updateOutboxProgress();
            showCurrentPart();
        }catch(Exception ex){showFailure(ex);}
    }

    private void updateOutboxProgress(){
        if(outboxProgress==null || drafts==null)return;
        try {
            if(drafts.pending()==null) {
                outboxProgress.setText("UTBOKS · Ingen uavklarte krypterte SMS-er");
            }else {
                outboxProgress.setText("UTBOKS · Venter på bekreftelse for del "+
                    (drafts.currentPartIndex()+1)+" av "+drafts.partCount());
            }
        }catch(Exception ex){
            outboxProgress.setText("UTBOKS · Sjekk lagret SMS-utkast");
        }
    }
    @Override protected void onResume(){
        super.onResume();
        updateOutboxProgress();
    }

    private void showCurrentPart(){
        try{
            String part=drafts.currentPart();
            if(part==null){
                status("Ingen uavklart kryptert SMS ligger i utboksen.");
                return;
            }
            String recipient=phone();
            int current=drafts.currentPartIndex()+1;
            int total=drafts.partCount();
            if(part.length()>153)
                throw new SecurityException("SMS-rammen er for lang");
            new AlertDialog.Builder(this).setTitle("Kryptert SMS · del "+current+"/"+total)
                .setMessage("Mottaker: "+recipient+". Nå åpner vi nøyaktig ÉN SMS-del (maks 153 tegn). "+
                    "Kontroller mottakeren og trykk Send i SMS-appen. "+
                    "Kom så tilbake og trykk «BEKREFT DENNE SMS-DELEN» "+
                    "for å gå videre. Hver del kan koste én SMS.")
                .setNegativeButton("Senere",null)
                .setPositiveButton("Åpne SMS-appen",(dialog,which)->{
                    try {
                        composeSms(part);
                        status("Kryptert del "+current+" av "+total+
                            " ligger i SMS-appen. Sending ikke bekreftet.");
                    }catch(Exception ex){showFailure(ex);}
                }).show();
        }catch(Exception ex){showFailure(ex);}
    }

    private void retryDraft(){
        showCurrentPart();
    }

    private void confirmDraft(){
        try{
            if(drafts.pending()==null){
                status("Ingen uavklart kryptert SMS-del å bekrefte.");
                return;
            }
            int current=drafts.currentPartIndex()+1;
            int total=drafts.partCount();
            new AlertDialog.Builder(this)
                .setTitle("Er del "+current+" av "+total+" sendt?")
                .setMessage("Bekreft KUN når du har sett at du trykket "+
                    "Send for akkurat denne delen i SMS-appen. "+
                    "BLCKCHAT kan ikke se operatørens sendestatus.")
                .setNegativeButton("Ikke ennå",null)
                .setPositiveButton("Ja, sendt",(dialog,which)->{
                    try {
                        boolean finished=drafts.confirmCurrentPart();
                        updateOutboxProgress();
                        if(finished)
                            status("Alle "+total+" SMS-deler er manuelt bekreftet "+
                                "sendt. Levering til mottakeren er ikke verifisert.");
                        else
                            showCurrentPart();
                    }catch(Exception ex){showFailure(ex);}
                }).show();
        }catch(Exception ex){showFailure(ex);}
    }

    private void discardDraft(){
        try{
            if(drafts.pending()==null){
                status("Ingen kryptert SMS å forkaste.");
                return;
            }
            new AlertDialog.Builder(this).setTitle("Forkaste kryptert SMS?")
                .setMessage("Utboksen slettes, selv om enkelte SMS-deler "+
                    "allerede kan være sendt. Ikke bruk denne funksjonen "+
                    "for å bekrefte sending.")
                .setNegativeButton("Behold",null)
                .setPositiveButton("Forkast utkast",(dialog,which)->{
                    try{
                        drafts.clear();
                        updateOutboxProgress();
                        status("Kryptert SMS-utkast forkastet.");
                    }catch(Exception ex){showFailure(ex);}
                }).show();
        }catch(Exception ex){showFailure(ex);}
    }

    private void decrypt(){
        try{
            String text=incoming.getText().toString().trim();
            SmsFrameAssembler.Result receipt=inbox.accept(text);
            incoming.setText("");
            if(receipt.state==SmsFrameAssembler.State.PENDING){
                status("Mottatt "+receipt.received+" av "+receipt.expected+
                    " krypterte SMS-deler. Lim inn neste del når den kommer.");
                return;
            }
            // Completion of SMS fragments is not proof of authenticity.
            String plain=crypto.decrypt(receipt.completeWire);
            inbox.acknowledged(receipt.id);
            decrypted.setText("MOTTATT OG AUTENTISERT\n"+plain);
            decrypted.setTextColor(FG);
            status("Alle SMS-deler mottatt. Integritet og duplikatsjekk bestått.");
        }catch(Exception ex){showFailure(ex);}
    }
    private void clearIncomplete(){
        new AlertDialog.Builder(this)
            .setTitle("Slette ufullstendige SMS-er?")
            .setMessage("Bare mellomlagrede krypterte SMS-deler slettes. "+
                "Ingen telefonmeldinger eller krypteringsnøkler påvirkes.")
            .setNegativeButton("Avbryt",null)
            .setPositiveButton("Slett delene",(dialog,which)->{
                try{inbox.clear();
                    status("Uferdige krypterte SMS-deler er slettet.");
                }catch(Exception ex){showFailure(ex);}
            }).show();
    }
    private void askForget(){
        new AlertDialog.Builder(this).setTitle("Fjerne paring?")
            .setMessage("Du mister kontaktverifiseringen og må utveksle nøkler "
                +"og sammenligne sikkerhetskoden på nytt. Lokal egen nøkkel beholdes.")
            .setNegativeButton("Avbryt",null)
            .setPositiveButton("Fjern paring",(d,w)->{
                try{crypto.forgetPeer();inbox.clear();drafts.clear();updateOutboxProgress();peerCode.setText("");
                    decrypted.setText("Ingen melding dekryptert.");
                    updatePairing();status("Paring fjernet.");
                }catch(Exception e){showFailure(e);}
            }).show();
    }
    private void receiveIntent(Intent intent){
        if(intent==null)return;
        if(Intent.ACTION_VIEW.equals(intent.getAction())){
            Uri link=intent.getData();
            if(link!=null && "blckchat".equals(link.getScheme()) &&
                "pair".equals(link.getHost())){
                String key=link.getQueryParameter("k");
                if(key!=null && key.matches("[A-Za-z0-9_-]{100,160}")){
                    peerCode.setText("BC42K|1|"+key);
                    status("Nøkkelen fra invitasjonen er fylt inn. Trykk IMPORTER OFFENTLIG NØKKEL.");
                }
            }
            return;
        }
        if(!Intent.ACTION_SEND.equals(intent.getAction()) ||
            !("text/plain".equals(intent.getType())))return;
        CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if(shared==null)return;
        String text=shared.toString().trim();
        if(text.length()>3500)return;
        if(text.startsWith("BC42|1|")){
            incoming.setText(text);
            status("SMS-tekst importert. Trykk Dekrypter når du har verifisert kontakten.");
        }else if(text.contains("BC42K|1|")){
            int i=text.indexOf("BC42K|1|");
            peerCode.setText(text.substring(i).split("\\s",2)[0]);
            status("Offentlig nøkkel importert som utkast. Trykk Importer.");
        }
    }
}
