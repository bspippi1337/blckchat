package com.blckswan.blckchat;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/**
 * Thin Android UI + network adapter for the experimental LAN chat transport.
 * No contacts, SMS-reading or storage permissions. The administrator opens
 * the system SMS compose dialog to send an invitation voluntarily.
 *
 * IMPORTANT: internal same-Wi-Fi test, not GitHub-backed production/E2EE.
 */
public final class MainActivity extends Activity implements LanChat.Events {
    static {
        System.loadLibrary("blcknative");
    }
    private static native int nativeAccent();
    private static native boolean nativeValidateMessage(String value);

    private static final int BG=0xff0a0f16;
    private static final int PANEL=0xff131f2a;
    private static final int ELEVATED=0xff1c2b38;
    private static final int BORDER=0xff344550;
    private static final int LIGHT=0xffebf4f5;
    private static final int MUTED=0xff92a9b3;
    private static final int OWN=0xff20594f;

    private int accent;
    private LinearLayout base;
    private LinearLayout feed;
    private ScrollView scroll;
    private TextView status;
    private EditText nameField;
    private EditText hostField;
    private EditText idField;
    private EditText keyField;
    private EditText phoneField;
    private EditText composer;
    private LanChat chat;
    private boolean admin;
    private String myName="PhoneA";
    private String pendingHost="", pendingId="", pendingKey="";
    private String latestInvite="";

    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable background(int color,int radius) {
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private GradientDrawable bordered(int color,int radius) {
        GradientDrawable drawable=background(color,radius);
        drawable.setStroke(dp(1),BORDER);
        return drawable;
    }
    private LinearLayout column() {
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView view=new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return view;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1,-2);
    }
    private void pad(View view,int lr,int tb) {
        view.setPadding(dp(lr),dp(tb),dp(lr),dp(tb));
    }
    private TextView header(String eyebrow,String headline,String subtitle) {
        TextView eye=text(eyebrow,11,accent,true);
        eye.setLetterSpacing(0.20f);
        base.addView(eye,matchWrap());
        TextView title=text(headline,33,LIGHT,true);
        title.setLetterSpacing(-0.045f);
        LinearLayout.LayoutParams tl=matchWrap();tl.topMargin=dp(14);
        base.addView(title,tl);
        TextView desc=text(subtitle,13,MUTED,false);
        desc.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams dl=matchWrap();dl.topMargin=dp(10);
        base.addView(desc,dl);
        return title;
    }
    private void root(boolean scrollable) {
        base=column();
        base.setBackgroundColor(BG);
        base.setPadding(dp(22),dp(30),dp(22),dp(18));
        if (scrollable) {
            ScrollView wrapper=new ScrollView(this);
            wrapper.setFillViewport(true);
            wrapper.setBackgroundColor(BG);
            wrapper.addView(base);
            setContentView(wrapper);
        } else {
            setContentView(base);
        }
    }
    private TextView button(String value,int color,int fg,View.OnClickListener click) {
        TextView btn=text(value,14,fg,true);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(dp(52));
        btn.setBackground(background(color,13));
        btn.setOnClickListener(click);
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }
    private void addButton(TextView b,int top) {
        LinearLayout.LayoutParams lp=matchWrap();
        lp.topMargin=dp(top);
        base.addView(b,lp);
    }
    private EditText field(String label,String hint,String initial) {
        TextView lab=text(label,11,accent,true);
        lab.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams lp=matchWrap();lp.topMargin=dp(17);
        base.addView(lab,lp);
        EditText e=new EditText(this);
        e.setTextColor(LIGHT);
        e.setHintTextColor(MUTED);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(false);
        e.setBackground(bordered(ELEVATED,12));
        e.setPadding(dp(15),0,dp(15),0);
        e.setHint(hint);
        e.setText(initial);
        e.setMinHeight(dp(51));
        LinearLayout.LayoutParams ep=matchWrap();ep.topMargin=dp(7);
        base.addView(e,ep);
        return e;
    }
    private void note(String contents,int top) {
        TextView note=text(contents,12,MUTED,false);
        note.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams lp=matchWrap();lp.topMargin=dp(top);
        base.addView(note,lp);
    }
    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        accent=nativeAccent();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        readIntent(getIntent());
        showLobby();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readIntent(intent);
        if(chat==null) showLobby();
    }

    private void readIntent(Intent intent) {
        if (intent==null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri u=intent.getData();
        if (u==null || !"blckchat".equals(u.getScheme()) ||
            !"join".equals(u.getHost())) return;
        String host=u.getQueryParameter("host");
        String id=u.getQueryParameter("id");
        String key=u.getQueryParameter("key");
        if (host!=null && id!=null && key!=null) {
            pendingHost=host;pendingId=id;pendingKey=key;
        }
    }

    private void showLobby() {
        root(true);
        boolean invited=!pendingHost.isEmpty() && !pendingId.isEmpty() && !pendingKey.isEmpty();
        if (invited) {
            header("BLCKCHAT / INVITASJON","Velkommen!",
                "PhoneA har invitert deg til The Workshop. Skriv navnet ditt og trykk Bli med. Du trenger ingen konto.");
            nameField=field("HVA HETER DU?","PhoneB eller PhoneC","");
            addButton(button("BLI MED I SAMTALEN",accent,BG,v->startJoining()),24);
            note("Begge telefonene må være på samme Wi-Fi eller Phone A sin hotspot. Invitasjonen kan bare brukes én gang, og varer i 10 minutter.",24);
        } else {
            header("BLCKCHAT / INTERN ALFA","En samtale, uten omveier.",
                "PhoneA åpner rommet. PhoneB og PhoneC kommer inn fra SMS-invitasjonen. Dette er en lokal test, ikke ferdig E2EE.");
            nameField=field("VISNINGSNAVN","PhoneA","PhoneA");
            addButton(button("ÅPNE ROM SOM ADMIN",accent,BG,v->startHosting()),24);
            note("Gjester trenger bare åpne SMS-lenken sin. For manuell tilkobling kan du fylle inn feltene under.",24);
            hostField=field("VERTENS WI-FI-ADRESSE","192.168.1.42","");
            idField=field("INVITASJONS-ID","Fra SMS-lenken","");
            keyField=field("INVITASJONSNØKKEL","Fra SMS-lenken","");
            addButton(button("KOBLE TIL MANUELT",ELEVATED,LIGHT,v->startJoining()),16);
        }
        TextView support=button("ENKEL BRUKSANVISNING",PANEL,accent,v->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(
                "https://bspippi1337.github.io/blckchat/guide.html"))));
        addButton(support,18);
    }

    private void startHosting() {
        String name=nameField.getText().toString().trim();
        if (!LanChat.safeName(name)) {
            setStatus("Please choose a simple name");
            nameField.setError("Only letters, digits, _ and -");
            return;
        }
        try {
            chat=new LanChat(this);
            chat.host(name);
            admin=true;
            myName=name;
            showChat(true);
        } catch (Exception e) {
            chat=null;
            onStatus("Could not start room: "+e.getMessage());
            nameField.setError("Host failed: "+e.getMessage());
        }
    }

    private void startJoining() {
        String name=nameField.getText().toString().trim();
        boolean invited=!pendingHost.isEmpty() && !pendingId.isEmpty() && !pendingKey.isEmpty();
        String host=invited?pendingHost:hostField.getText().toString().trim();
        String id=invited?pendingId:idField.getText().toString().trim();
        String key=invited?pendingKey:keyField.getText().toString().trim();
        if (!LanChat.safeName(name)) {
            nameField.setError("Choose a display name");
            return;
        }
        if (!host.matches("[0-9.]{7,15}") || !id.matches("[A-Za-z0-9_-]{16,64}") ||
            !key.matches("[A-Za-z0-9_-]{40,64}")) {
            if (invited) nameField.setError("Ugyldig invitasjon. Be PhoneA om en ny SMS.");
            else hostField.setError("Åpne hele invitasjonslenken eller fyll inn feltene.");
            return;
        }
        admin=false;
        myName=name;
        chat=new LanChat(this);
        showChat(false);
        chat.connect(new LanChat.Link(host,id,key),name);
    }

    private void showChat(boolean hosting) {
        root(false);
        header(hosting?"BLCKCHAT / PHONE A HOST":"BLCKCHAT / GUEST",
            "The Workshop",hosting?"Encrypted Wi-Fi group · This phone is the room host"
                :"Private Wi-Fi room · Not production E2EE");
        status=text("Connecting…",12,accent,false);
        LinearLayout.LayoutParams stat=matchWrap();stat.topMargin=dp(12);
        base.addView(status,stat);
        if (hosting) {
            TextView ip=text("SAME WI-FI · HOST "+LanChat.localIp()+":"+LanChat.PORT,11,MUTED,true);
            LinearLayout.LayoutParams ilp=matchWrap();ilp.topMargin=dp(8);
            base.addView(ip,ilp);
            phoneField=field("INVITE BY SMS · PHONE NUMBER","+47...", "");
            addButton(button("CREATE INVITE & OPEN SMS",ELEVATED,accent,v->sendInvite()),12);
            TextView copy=button("COPY LAST INVITE LINK",PANEL,MUTED,v->copyInvite());
            addButton(copy,8);
        }
        ScrollView messageScroll=new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setVerticalScrollBarEnabled(false);
        messageScroll.setBackground(bordered(PANEL,16));
        feed=column();
        feed.setPadding(dp(12),dp(16),dp(12),dp(16));
        messageScroll.addView(feed);
        LinearLayout.LayoutParams scrolling=new LinearLayout.LayoutParams(-1,0,1f);
        scrolling.topMargin=dp(14);
        base.addView(messageScroll,scrolling);
        scroll=messageScroll;
        addBubble("System","Connection started. Only use this experimental build for non-sensitive test messages.");
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        composer=new EditText(this);
        composer.setTextColor(LIGHT);
        composer.setTextSize(15);
        composer.setHintTextColor(MUTED);
        composer.setHint("Write a message…");
        composer.setMaxLines(3);
        composer.setMinLines(1);
        composer.setPadding(dp(14),dp(5),dp(12),dp(5));
        composer.setBackground(bordered(ELEVATED,13));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(54),1f);
        row.addView(composer,cp);
        TextView send=button("↑",accent,BG,v->submitMessage());
        send.setTextSize(26);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(54),dp(54));
        sp.leftMargin=dp(9);
        row.addView(send,sp);
        LinearLayout.LayoutParams rp=matchWrap();rp.topMargin=dp(12);
        base.addView(row,rp);
    }

    private void submitMessage() {
        String body=composer.getText().toString().trim();
        if (!nativeValidateMessage(body)) {
            composer.setError("Message must be 1–2048 bytes and contain no control characters");
            return;
        }
        if (chat==null || !chat.send(body)) {
            setStatus("Not connected. Message not sent.");
            return;
        }
        composer.setText("");
    }

    private void sendInvite() {
        if (chat==null || !admin) return;
        String phone=phoneField.getText().toString().trim();
        if (!phone.matches("\\+[1-9][0-9]{7,14}")) {
            phoneField.setError("Use an international number, e.g. +4799999999");
            return;
        }
        try {
            LanChat.Link invite=chat.newInvite(LanChat.localIp());
            latestInvite=invite.asUri();
            String page="https://bspippi1337.github.io/blckchat/join.html#lan="
                +invite.host+"~"+invite.id+"~"+invite.key;
            String message="Hei! PhoneA inviterer deg til BLCKCHAT-testen. "+
                "1) Åpne lenken og installer appen. "+
                "2) Åpne samme SMS-lenke igjen og trykk «Åpne BLCKCHAT». "+
                "Bruk samme Wi-Fi. Lenken varer i 10 minutter. "+page;
            Intent sms=new Intent(Intent.ACTION_SENDTO);
            sms.setData(Uri.parse("smsto:"+Uri.encode(phone)));
            sms.putExtra("sms_body",message);
            startActivity(sms);
            setStatus("Invitation created. Confirm send in your SMS app.");
        } catch(Exception e) {
            setStatus("Could not create invitation: "+e.getMessage());
        }
    }

    private void copyInvite() {
        if (latestInvite.isEmpty()) {
            setStatus("Create an SMS invitation first.");
            return;
        }
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("BLCKCHAT invitation",latestInvite));
        setStatus("Invitation copied. Keep it private.");
    }

    private void addBubble(String author,String body) {
        if (feed==null) return;
        boolean own=author.equals(myName);
        LinearLayout bubble=column();
        bubble.setPadding(dp(13),dp(9),dp(13),dp(11));
        bubble.setBackground(background(own?OWN:ELEVATED,14));
        TextView label=text(author.toUpperCase(Locale.ROOT),10,accent,true);
        label.setLetterSpacing(0.08f);
        bubble.addView(label,matchWrap());
        TextView content=text(body,15,LIGHT,false);
        content.setLineSpacing(dp(3),1f);
        LinearLayout.LayoutParams bodyParams=matchWrap();bodyParams.topMargin=dp(4);
        bubble.addView(content,bodyParams);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.gravity=own?Gravity.END:Gravity.START;
        lp.topMargin=dp(9);
        bubble.setMinimumWidth(dp(105));
        feed.addView(bubble,lp);
        if (feed.getChildCount()>150) feed.removeViewAt(0);
        if (scroll!=null) scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override public void onStatus(String message) {
        runOnUiThread(()->setStatus(message));
    }

    @Override public void onMessage(String name,String text) {
        runOnUiThread(()->addBubble(name,text));
    }

    @Override protected void onDestroy() {
        if (chat!=null) chat.shutdown();
        super.onDestroy();
    }
}
