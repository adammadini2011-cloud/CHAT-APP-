package com.chatapp.mobile;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.LayoutInflater;
import android.util.Base64;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root, content, toolbar;
    private String currentConversationId = "";
    private String currentConversationTitle = "";
    private TextView typingIndicator;
    private Runnable typingUpdateRunnable;
    private SupabaseRealtime realtime;
    private int realtimeGeneration = 0;
    private boolean activityStarted = false;
    private final Runnable presenceHeartbeat = new Runnable(){@Override public void run(){if(!isFinishing()){setPresence(true);handler.postDelayed(this,60000L);}}};
    private boolean inChat = false;
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (inChat && activityStarted && !isFinishing()) loadMessages(false);
            if (inChat && activityStarted) handler.postDelayed(this, 5000);
        }
    };

    private String appLanguage(){
        android.content.SharedPreferences prefs=getSharedPreferences("app_settings",MODE_PRIVATE);
        String saved=prefs.getString("language","");
        if(saved.isEmpty()){
            String device=Locale.getDefault().getLanguage();
            saved="ar".equalsIgnoreCase(device)?"ar":"en";
            prefs.edit().putString("language",saved).apply();
        }
        return saved;
    }
    private boolean isArabic(){return "ar".equals(appLanguage());}
    private String tr(String s){
        if(!isArabic()||s==null)return s;
        switch(s){
            case "Sign in":return "تسجيل الدخول";
            case "Continue with Google":return "المتابعة باستخدام Google";
            case "Continue with Facebook":return "المتابعة باستخدام Facebook";
            case "Show":return "إظهار";
            case "Hide":return "إخفاء";
            case "Create account":return "إنشاء حساب";
            case "Email address":return "البريد الإلكتروني";
            case "Password":return "كلمة المرور";
            case "Your chats":return "محادثاتك";
            case "Find people":return "ابحث عن أشخاص";
            case "New group":return "مجموعة جديدة";
            case "Profile":return "الملف الشخصي";
            case "Search":return "بحث";
            case "Back":return "رجوع";
            case "Send":return "إرسال";
            case "Write a message…":return "اكتب رسالة…";
            case "Create group":return "إنشاء مجموعة";
            case "Group name":return "اسم المجموعة";
            case "Members":return "الأعضاء";
            case "Leave group":return "مغادرة المجموعة";
            case "Save profile":return "حفظ الملف الشخصي";
            case "Display name":return "الاسم الظاهر";
            case "Username":return "اسم المستخدم";
            case "About you":return "نبذة عنك";
            case "Sign out":return "تسجيل الخروج";
            case "Edit message":return "تعديل الرسالة";
            case "Delete message":return "حذف الرسالة";
            case "Cancel":return "إلغاء";
            case "Language: English":return "اللغة: العربية";
            case "Someone is typing…":return "يكتب الآن…";
            default:return s;
        }
    }
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(tr(value)); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(tr(label)); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF245BFF,0xFF713BEB});
        shape.setCornerRadius(dp(16)); b.setBackground(shape); b.setPadding(dp(16),0,dp(16),0);
        b.setStateListAnimator(null); b.setElevation(dp(2));
        return b;
    }
    private void base(String title, String action, View.OnClickListener listener) {
        root = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.activity_main, null);
        int direction=isArabic()?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR;
        root.setLayoutDirection(direction);
        setContentView(root);
        toolbar = root.findViewById(R.id.toolbar);
        content = root.findViewById(R.id.content);
        toolbar.setLayoutDirection(direction);content.setLayoutDirection(direction);
        toolbar.removeAllViews();content.removeAllViews();
        TextView heading=text(title,20,Color.WHITE,true);
        toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if (action != null) { Button a=button(action); toolbar.addView(a); a.setOnClickListener(listener); }
    }
    private void scheduleNotifications() {
        if(ChatSession.token(this).isEmpty())return;
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            android.content.SharedPreferences settings=getSharedPreferences("app_settings",MODE_PRIVATE);
            if(!settings.getBoolean("notification_permission_requested",false)){
                settings.edit().putBoolean("notification_permission_requested",true).apply();
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},8201);
            }
        }
        PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(ChatNotificationWorker.class,15,TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("chat-message-check",ExistingPeriodicWorkPolicy.KEEP,request);
    }
    private void message(String s) { Toast.makeText(this,tr(s),Toast.LENGTH_LONG).show(); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(handleAuthRedirect(getIntent())) return;
        String token=ChatSession.token(this);
        if (token.isEmpty()) showAuth(); else showHome();
    }
    private void showAuth() {
        inChat=false; base("Infinity Chat","",null);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24),dp(28),dp(24),dp(18));
        TextView brand=text("∞",58,0xFF21D8FF,true); brand.setGravity(Gravity.CENTER);
        content.addView(brand,match(dp(70)));
        TextView title=text("Infinity Chat",29,Color.WHITE,true); title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView sub=text("Your conversations, connected.",14,0xFFB8C5E8,false); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp=match(dp(42)); sp.bottomMargin=dp(18); content.addView(sub,sp);
        EditText email=field("Email address",InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        content.addView(email,match(dp(54)));
        LinearLayout passwordRow=new LinearLayout(this); passwordRow.setOrientation(LinearLayout.HORIZONTAL);
        passwordRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText pass=field("Password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordRow.addView(pass,new LinearLayout.LayoutParams(0,dp(54),1));
        Button visibility=button("Show");
        LinearLayout.LayoutParams eyeParams=new LinearLayout.LayoutParams(dp(82),dp(50)); eyeParams.leftMargin=dp(8);
        passwordRow.addView(visibility,eyeParams);
        content.addView(passwordRow,match(dp(58)));
        visibility.setOnClickListener(v->{
            boolean showing=(pass.getInputType()&InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            int cursor=pass.getSelectionStart();
            pass.setInputType(showing?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            pass.setSelection(Math.max(0,Math.min(cursor,pass.length())));
            visibility.setText(showing?"Show":"Hide");
        });
        Button signIn=button("Sign in"), signUp=button("Create account");
        LinearLayout.LayoutParams bp=match(dp(52)); bp.topMargin=dp(14); content.addView(signIn,bp);
        LinearLayout.LayoutParams bp2=match(dp(52)); bp2.topMargin=dp(8); content.addView(signUp,bp2);
        TextView divider=text("OR CONTINUE WITH",11,0xFF8998BE,true);
        divider.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dpv=match(dp(42)); dpv.topMargin=dp(10); content.addView(divider,dpv);
        Button google=socialButton("Continue with Google",0xFFFFFFFF,0xFF18213D);
        Button facebook=socialButton("Continue with Facebook",0xFF1877F2,Color.WHITE);
        LinearLayout.LayoutParams gp=match(dp(50)); gp.topMargin=dp(5); content.addView(google,gp);
        LinearLayout.LayoutParams fp=match(dp(50)); fp.topMargin=dp(8); content.addView(facebook,fp);
        TextView status=text("",12,0xFFFFA8B5,false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st=match(dp(52)); st.topMargin=dp(8); content.addView(status,st);
        signIn.setOnClickListener(v->auth(email.getText().toString().trim(),pass.getText().toString(),false,status));
        signUp.setOnClickListener(v->auth(email.getText().toString().trim(),pass.getText().toString(),true,status));
        google.setOnClickListener(v->startSocialLogin("google",status));
        facebook.setOnClickListener(v->startSocialLogin("facebook",status));
    }
    private Button socialButton(String label,int background,int foreground) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(foreground);
        b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable shape=new GradientDrawable(); shape.setColor(background); shape.setCornerRadius(dp(15));
        b.setBackground(shape); b.setStateListAnimator(null); return b;
    }
    private void startSocialLogin(String provider,TextView status) {
        try {
            String redirect="infinitychat://auth/callback";
            String url=SupabaseConfig.PROJECT_URL+"/auth/v1/authorize?provider="+provider+"&redirect_to="+SupabaseClient.enc(redirect);
            status.setText("Opening "+(provider.equals("google")?"Google":"Facebook")+" sign-in…");
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        } catch(Exception e) { status.setText("Could not start sign-in: "+e.getMessage()); }
    }
    private boolean handleAuthRedirect(Intent intent) {
        if(intent==null||intent.getData()==null)return false;
        Uri uri=intent.getData();
        if(!"infinitychat".equals(uri.getScheme())||!"auth".equals(uri.getHost()))return false;
        String raw=uri.getFragment();
        if(raw==null||raw.isEmpty())raw=uri.getEncodedQuery();
        if(raw==null)raw="";
        java.util.Map<String,String> params=new java.util.HashMap<>();
        try {
            for(String pair:raw.split("&")) {
                int at=pair.indexOf('=');
                if(at>0)params.put(java.net.URLDecoder.decode(pair.substring(0,at),"UTF-8"),java.net.URLDecoder.decode(pair.substring(at+1),"UTF-8"));
            }
            String error=params.get("error_description");
            if(error==null)error=params.get("error");
            String access=params.get("access_token"), refresh=params.get("refresh_token");
            if(error!=null&&!error.isEmpty()){message("Sign-in failed: "+error);return true;}
            if(access==null||access.isEmpty()||refresh==null||refresh.isEmpty()){message("Sign-in did not return a valid session.");return true;}
            String userId="",userEmail="";
            String[] parts=access.split("\\.");
            if(parts.length>1) {
                JSONObject claims=new JSONObject(new String(Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),java.nio.charset.StandardCharsets.UTF_8));
                userId=claims.optString("sub","");
                userEmail=claims.optString("email","");
            }
            if(userId.isEmpty()){message("Could not read the signed-in account. Please try again.");return true;}
            ChatSession.save(this,access,refresh,userId,userEmail);
            message("Signed in successfully.");
            showHome();
            return true;
        } catch(Exception e) { message("Could not finish sign-in: "+e.getMessage()); return true; }
    }
    private EditText field(String hint,int type) {
        EditText e=new EditText(this); boolean multiline=(type&InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0; e.setSingleLine(!multiline); if(multiline){e.setMaxLines(4);e.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);} e.setHint(tr(hint)); e.setInputType(type);
        e.setTextColor(Color.WHITE); e.setHintTextColor(0xFFAAB6C8); e.setPadding(dp(14),0,dp(14),0);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF35C7A5)); return e;
    }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1,height); }
    private void auth(String email,String password,boolean signUp,TextView status) {
        if(email.isEmpty()||password.length()<6){status.setText("Enter a valid email and password (6+ characters).");return;}
        status.setText("Connecting…");
        io.execute(()->{
            try{
                JSONObject r=SupabaseAuth.authenticate(email,password,signUp);
                String access=r.optString("access_token","");
                String refresh=r.optString("refresh_token","");
                JSONObject user=r.optJSONObject("user");
                String uid=user==null?"":user.optString("id","");
                if(!access.isEmpty()){
                    ChatSession.save(this,access,refresh,uid,email);
                    runOnUiThread(this::showHome);
                }else{
                    runOnUiThread(()->status.setText("Account request sent. If email confirmation is enabled, confirm your email, then sign in."));
                }
            }catch(Exception e){runOnUiThread(()->status.setText(e.getMessage()));}
        });
    }
    private String isoAt(long time) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC")); return f.format(new Date(time));
    }
    private String nowIso(){return isoAt(System.currentTimeMillis());}
    private void setTypingStatus(String conversationId,boolean typing){
        io.execute(()->{try{JSONObject row=new JSONObject();row.put("conversation_id",conversationId);row.put("user_id",uid());row.put("is_typing",typing);row.put("updated_at",nowIso());SupabaseClient.upsert("/rest/v1/typing_status?on_conflict=conversation_id,user_id",token(),row);}catch(Exception ignored){}});
    }
    private String token(){String value=ChatSession.validToken(this);if(realtime!=null)realtime.updateAccessToken(value);return value;}
    private void stopRealtime(){
        realtimeGeneration++;
        if(realtime!=null){realtime.stop();realtime=null;}
    }
    private void startRealtime(String cid){
        stopRealtime();
        final int generation=realtimeGeneration;
        io.execute(()->{
            String access=ChatSession.validToken(getApplicationContext());
            runOnUiThread(()->{
                if(generation!=realtimeGeneration||!activityStarted||!inChat||!cid.equals(currentConversationId)||access.isEmpty())return;
                SupabaseRealtime channel=new SupabaseRealtime();
                realtime=channel;
                channel.start(getApplicationContext(),cid,access,new SupabaseRealtime.Listener(){
                    @Override public void onMessageChange(){
                        if(activityStarted&&inChat&&cid.equals(currentConversationId))handler.postDelayed(()->{if(inChat&&cid.equals(currentConversationId))loadMessages(false);},180L);
                    }
                    @Override public void onState(boolean connected,String detail){}
                });
            });
        });
    }
    private String uid(){return ChatSession.userId(this);}
    private void showHome() {
        stopRealtime();
        scheduleNotifications();
        setPresence(true);
        inChat=false; handler.removeCallbacks(refreshRunnable);
        base("Your chats","＋",v->showSearch());
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button find=button("Find people"), group=button("New group"), profile=button("Profile");
        actions.addView(find,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(group,new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(profile,new LinearLayout.LayoutParams(0,dp(48),1));
        content.addView(actions); find.setOnClickListener(v->showSearch()); group.setOnClickListener(v->createGroupDialog());
        profile.setOnClickListener(v->showProfile());
        TextView hint=text("Private messages and groups",13,0xFFAAB6C8,false);
        LinearLayout.LayoutParams hp=match(dp(34)); hp.topMargin=dp(12); content.addView(hint,hp);
        ScrollView scroll=new ScrollView(this); content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        TextView loading=text("Loading conversations…",14,0xFFAAB6C8,false); list.addView(loading);
        io.execute(()->{
            try{
                String path="/rest/v1/conversation_members?select=conversation_id,conversations(id,title,is_group,updated_at)&user_id=eq."+SupabaseClient.enc(uid())+"&order=joined_at.desc";
                JSONArray rows=SupabaseClient.getArray(path,token());
                List<ChatItem> items=new ArrayList<>();
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.optJSONObject(i); JSONObject c=row==null?null:row.optJSONObject("conversations");
                    if(c==null && row!=null && row.optJSONArray("conversations")!=null && row.optJSONArray("conversations").length()>0)c=row.optJSONArray("conversations").optJSONObject(0);
                    if(c!=null)items.add(new ChatItem(c.optString("id"),c.optString("title"),c.optBoolean("is_group")));
                }
                runOnUiThread(()->{
                    list.removeAllViews();
                    if(items.isEmpty()){list.addView(text("No conversations yet. Find someone to start chatting.",15,0xFFAAB6C8,false));return;}
                    for(ChatItem item:items){
                        Button rowButton=button((item.group?"👥  ":"●  ")+item.title+"    ›");
                        rowButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
                        LinearLayout.LayoutParams p=match(dp(62)); p.bottomMargin=dp(7); list.addView(rowButton,p);
                        rowButton.setOnClickListener(v->openChat(item));
                    }
                });
            }catch(Exception e){runOnUiThread(()->{list.removeAllViews();list.addView(text("Could not load chats: "+e.getMessage(),13,0xFFF2A7A7,false));});}
        });
    }
    private void showSearch() {
        stopRealtime();
        inChat=false; handler.removeCallbacks(refreshRunnable);
        base("Find people","Back",v->showHome());
        EditText query=field("Search by username or name",InputType.TYPE_CLASS_TEXT);
        content.addView(query,match(dp(54))); Button go=button("Search"); content.addView(go,match(dp(48)));
        ScrollView sc=new ScrollView(this); content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); sc.addView(results);
        go.setOnClickListener(v->searchProfiles(query.getText().toString().trim(),results));
        query.setOnEditorActionListener((v,id,event)->{searchProfiles(query.getText().toString().trim(),results);return true;});
    }
    private void searchProfiles(String q,LinearLayout results) {
        if(q.length()<2){message("Type at least 2 letters.");return;}
        results.removeAllViews(); results.addView(text("Searching…",14,0xFFAAB6C8,false));
        io.execute(()->{
            try{
                String term="*"+q.replace("*","")+"*";
                String path="/rest/v1/profiles?select=id,username,display_name,bio,is_online,last_seen_at& id=neq."+SupabaseClient.enc(uid())+
                    "&or=(username.ilike."+SupabaseClient.enc(term)+",display_name.ilike."+SupabaseClient.enc(term)+")&limit=30";
                path=path.replace("&%20id","&id").replace("& id","&id");
                JSONArray users=SupabaseClient.getArray(path,token());
                runOnUiThread(()->{
                    results.removeAllViews();
                    if(users.length()==0){results.addView(text("No users found.",14,0xFFAAB6C8,false));return;}
                    for(int i=0;i<users.length();i++){
                        JSONObject u=users.optJSONObject(i); if(u==null)continue;
                        String id=u.optString("id"), name=u.optString("display_name",u.optString("username","User"));
                        String username=u.optString("username","");
                        String seen=u.optString("last_seen_at","");
                        String presence=u.optBoolean("is_online")?(isArabic()?"  • متصل الآن":"  • online")
                            :(!seen.isEmpty()&&seen.length()>=16?(isArabic()?"  • آخر ظهور ":"  • last seen ")+seen.substring(0,16).replace('T',' '):"");
                        Button b=button(name+(username.isEmpty()?"":"  @"+username)+presence);
                        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); LinearLayout.LayoutParams p=match(dp(58));p.bottomMargin=dp(6);results.addView(b,p);
                        b.setOnClickListener(v->startDirect(id,name));
                        b.setOnLongClickListener(v->{showUserActions(id,name);return true;});
                    }
                });
            }catch(Exception e){runOnUiThread(()->{results.removeAllViews();results.addView(text(e.getMessage(),13,0xFFF2A7A7,false));});}
        });
    }
    private void startDirect(String otherId,String name) {
        message("Opening conversation…");
        io.execute(()->{
            try{
                JSONArray blockedByMe=SupabaseClient.getArray("/rest/v1/user_blocks?select=blocked_id&blocker_id=eq."+SupabaseClient.enc(uid())+"&blocked_id=eq."+SupabaseClient.enc(otherId),token());
                JSONArray blockedMe=SupabaseClient.getArray("/rest/v1/user_blocks?select=blocker_id&blocker_id=eq."+SupabaseClient.enc(otherId)+"&blocked_id=eq."+SupabaseClient.enc(uid()),token());
                if(blockedByMe.length()>0||blockedMe.length()>0){runOnUiThread(()->message("This conversation is unavailable because one of you has blocked the other."));return;}
                JSONArray mine=SupabaseClient.getArray("/rest/v1/conversation_members?select=conversation_id&user_id=eq."+SupabaseClient.enc(uid()),token());
                for(int i=0;i<mine.length();i++){
                    String existingId=mine.getJSONObject(i).optString("conversation_id");
                    JSONArray other=SupabaseClient.getArray("/rest/v1/conversation_members?select=user_id&conversation_id=eq."+SupabaseClient.enc(existingId)+"&user_id=eq."+SupabaseClient.enc(otherId),token());
                    if(other.length()>0){
                        JSONArray details=SupabaseClient.getArray("/rest/v1/conversations?select=title,is_group&id=eq."+SupabaseClient.enc(existingId)+"&is_group=eq.false&limit=1",token());
                        if(details.length()>0){String title=details.getJSONObject(0).optString("title",name);runOnUiThread(()->openChat(new ChatItem(existingId,title,false)));return;}
                    }
                }
                JSONObject conv=new JSONObject(); conv.put("is_group",false);conv.put("created_by",uid());conv.put("title",name);
                JSONArray created=SupabaseClient.post("/rest/v1/conversations",token(),conv);
                String cid=created.getJSONObject(0).getString("id");
                JSONObject me=new JSONObject();me.put("conversation_id",cid);me.put("user_id",uid());me.put("role","member");
                SupabaseClient.post("/rest/v1/conversation_members",token(),me);
                JSONObject other=new JSONObject();other.put("conversation_id",cid);other.put("user_id",otherId);other.put("role","member");
                SupabaseClient.post("/rest/v1/conversation_members",token(),other);
                runOnUiThread(()->openChat(new ChatItem(cid,name,false)));
            }catch(Exception e){runOnUiThread(()->message("Could not start chat: "+e.getMessage()));}
        });
    }
    private void openChat(ChatItem item) {
        currentConversationId=item.id; currentConversationTitle=item.title; inChat=true;
        base(item.title,item.group?"Manage":"Back",v->{if(item.group){setTypingStatus(currentConversationId,false);showGroupManagement();}else{setTypingStatus(currentConversationId,false);inChat=false;handler.removeCallbacks(refreshRunnable);showHome();}});
        ScrollView sc=new ScrollView(this); sc.setFillViewport(false); content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        typingIndicator=text("",12,0xFF35C7A5,false);content.addView(typingIndicator,match(dp(24)));
        LinearLayout messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); sc.addView(messages);
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(0,dp(8),0,0);
        EditText input=field("Write a message…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){}
            @Override public void afterTextChanged(Editable value){
                if(typingUpdateRunnable!=null)handler.removeCallbacks(typingUpdateRunnable);
                final String cid=currentConversationId;final boolean typing=value.length()>0;
                typingUpdateRunnable=()->setTypingStatus(cid,typing);
                handler.postDelayed(typingUpdateRunnable,450);
            }
        });
        Button attach=button("＋"); Button send=button("Send");
        composer.addView(attach,new LinearLayout.LayoutParams(dp(48),dp(52))); composer.addView(input,new LinearLayout.LayoutParams(0,dp(52),1)); composer.addView(send,new LinearLayout.LayoutParams(dp(82),dp(52)));
        attach.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("*/*");startActivityForResult(pick,410);});
        content.addView(composer);
        send.setOnClickListener(v->{String body=input.getText().toString().trim();if(!body.isEmpty()){input.setText("");sendMessage(body);}});
        startRealtime(item.id);
        loadMessages(true); handler.removeCallbacks(refreshRunnable); handler.postDelayed(refreshRunnable,5000);
    }
    private void loadMessages(boolean showLoading) {
        final String cid=currentConversationId;
        if(cid.isEmpty())return;
        // Fetch in the background; refresh the current chat without blocking typing.
        io.execute(()->{
            try{
                String path="/rest/v1/messages?select=id,sender_id,body,created_at,attachment_path,attachment_type,deleted_at,edited_at,message_receipts(user_id,delivered_at,read_at)&conversation_id=eq."+SupabaseClient.enc(cid)+"&deleted_at=is.null&order=created_at.asc&limit=200";
                JSONArray rows=SupabaseClient.getArray(path,token());
                String stamp=nowIso();
                for(int i=0;i<rows.length();i++){
                    JSONObject incoming=rows.optJSONObject(i);
                    if(incoming!=null && !uid().equals(incoming.optString("sender_id"))){
                        boolean alreadyRead=false;
                        JSONArray receipts=incoming.optJSONArray("message_receipts");
                        if(receipts!=null)for(int r=0;r<receipts.length();r++){
                            JSONObject receiptRow=receipts.optJSONObject(r);
                            if(receiptRow!=null && uid().equals(receiptRow.optString("user_id")) && !receiptRow.isNull("read_at")){alreadyRead=true;break;}
                        }
                        if(!alreadyRead){
                            JSONObject receipt=new JSONObject();
                            receipt.put("message_id",incoming.optString("id"));
                            receipt.put("user_id",uid());
                            receipt.put("delivered_at",stamp);
                            receipt.put("read_at",stamp);
                            SupabaseClient.upsert("/rest/v1/message_receipts?on_conflict=message_id,user_id",token(),receipt);
                        }
                    }
                }
                String cutoff=SupabaseClient.enc(isoAt(System.currentTimeMillis()-12000L));
                JSONArray typing=SupabaseClient.getArray("/rest/v1/typing_status?select=user_id&conversation_id=eq."+SupabaseClient.enc(cid)+"&user_id=neq."+SupabaseClient.enc(uid())+"&is_typing=eq.true&updated_at=gt."+cutoff,token());
                runOnUiThread(()->{if(typingIndicator!=null&&inChat&&cid.equals(currentConversationId)){typingIndicator.setText(typing.length()>0?tr("Someone is typing…"):"");renderMessages(rows);}});
            }catch(Exception e){if(showLoading)runOnUiThread(()->message("Messages: "+e.getMessage()));}
        });
    }
    private void renderMessages(JSONArray rows) {
        if(!inChat||content==null)return;
        ScrollView sc=null; LinearLayout msgs=null;
        for(int i=0;i<content.getChildCount();i++){
            View v=content.getChildAt(i); if(v instanceof ScrollView){sc=(ScrollView)v; if(sc.getChildCount()>0&&sc.getChildAt(0) instanceof LinearLayout)msgs=(LinearLayout)sc.getChildAt(0);break;}
        }
        if(msgs==null)return; msgs.removeAllViews();
        if(rows.length()==0)msgs.addView(text("Say hello 👋",14,0xFFAAB6C8,false));
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.optJSONObject(i);if(m==null)continue;
            boolean mine=uid().equals(m.optString("sender_id"));
            String attachment=m.optString("attachment_path","");
            String body=m.optString("body","");
            if(!attachment.isEmpty())body="📎 Open attachment ("+m.optString("attachment_type","file")+")";
            TextView bubble=text(body,15,mine?Color.WHITE:0xFFE6EDF7,false);
            bubble.setPadding(dp(14),dp(10),dp(14),dp(10));
            GradientDrawable bubbleShape=new GradientDrawable();bubbleShape.setColor(mine?0xFF176B63:0xFF263750);bubbleShape.setCornerRadius(dp(16));bubble.setBackground(bubbleShape);
            if(!attachment.isEmpty())bubble.setOnClickListener(v->openAttachment(attachment));
            LinearLayout line=new LinearLayout(this);line.setGravity(mine?Gravity.END:Gravity.START);
            if(mine) {
                JSONArray receipts=m.optJSONArray("message_receipts");
                boolean delivered=false, read=false;
                if(receipts!=null) for(int ri=0;ri<receipts.length();ri++){JSONObject rr=receipts.optJSONObject(ri);if(rr!=null){delivered|=!rr.isNull("delivered_at");read|=!rr.isNull("read_at");}}
                TextView status=text(read?"✓✓ Seen":(delivered?"✓✓ Delivered":"✓ Sent"),10,read?0xFF35C7A5:0xFFAAB6C8,false);
                LinearLayout bubbleWrap=new LinearLayout(this);bubbleWrap.setOrientation(LinearLayout.VERTICAL);bubbleWrap.addView(bubble);bubbleWrap.addView(status);
                bubbleWrap.setOnLongClickListener(v->{showMessageActions(m);return true;});
                if(!attachment.isEmpty()) bubbleWrap.setOnClickListener(v->openAttachment(attachment));
                line.addView(bubbleWrap,new LinearLayout.LayoutParams(-2,-2));
            } else {
                bubble.setOnLongClickListener(v->{return false;});
                line.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
            }
            LinearLayout.LayoutParams p=match(-2);p.topMargin=dp(5);msgs.addView(line,p);
        }
        final ScrollView scrollTarget=sc;
        if(scrollTarget!=null)scrollTarget.post(()->scrollTarget.fullScroll(View.FOCUS_DOWN));
    }
    private void openAttachment(String path) {
        io.execute(()->{try{String url=SupabaseStorage.signedUrl(path,token());Intent view=new Intent(Intent.ACTION_VIEW,Uri.parse(url));runOnUiThread(()->{try{startActivity(view);}catch(Exception e){message("No app can open this file.");}});}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==410&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri selected=data.getData();String cid=currentConversationId;message("Uploading attachment…");
            io.execute(()->{try{String uploaded=SupabaseStorage.upload(this,selected,cid,token());String[] parts=uploaded.split("\\|",2);JSONObject msg=new JSONObject();msg.put("conversation_id",cid);msg.put("sender_id",uid());msg.put("attachment_path",parts[0]);msg.put("attachment_type",parts.length>1?parts[1]:"file");SupabaseClient.post("/rest/v1/messages",token(),msg);runOnUiThread(()->{message("Attachment sent.");loadMessages(false);});}catch(Exception e){runOnUiThread(()->message("Upload failed: "+e.getMessage()));}});
        }
    }
    private void sendMessage(String body) {
        final String cid=currentConversationId;
        io.execute(()->{
            try{
                JSONObject m=new JSONObject();m.put("conversation_id",cid);m.put("sender_id",uid());m.put("body",body);
                SupabaseClient.post("/rest/v1/messages",token(),m);
                runOnUiThread(()->loadMessages(false));
            }catch(Exception e){runOnUiThread(()->message("Message not sent: "+e.getMessage()));}
        });
    }
    private void createGroupDialog() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),0,dp(16),0);
        EditText title=field("Group name",InputType.TYPE_CLASS_TEXT);
        EditText usernames=field("Participant usernames, comma separated",InputType.TYPE_CLASS_TEXT);
        box.addView(title,match(dp(54)));box.addView(usernames,match(dp(54)));
        new AlertDialog.Builder(this).setTitle("Create group").setView(box)
            .setPositiveButton("Create",(d,w)->createGroup(title.getText().toString().trim(),usernames.getText().toString()))
            .setNegativeButton("Cancel",null).show();
    }
    private void createGroup(String title,String names) {
        if(title.isEmpty()||names.trim().isEmpty()){message("Enter a group name and at least one username.");return;}
        io.execute(()->{
            try{
                JSONObject c=new JSONObject();c.put("title",title);c.put("is_group",true);c.put("created_by",uid());
                String cid=SupabaseClient.post("/rest/v1/conversations",token(),c).getJSONObject(0).getString("id");
                JSONObject self=new JSONObject();self.put("conversation_id",cid);self.put("user_id",uid());self.put("role","owner");
                SupabaseClient.post("/rest/v1/conversation_members",token(),self);
                String[] handles=names.split(",");
                for(String h:handles){
                    String handle=h.trim().replace("@","");
                    if(handle.isEmpty())continue;
                    JSONArray found=SupabaseClient.getArray("/rest/v1/profiles?select=id&username=eq."+SupabaseClient.enc(handle)+"&limit=1",token());
                    if(found.length()>0){
                        String memberId=found.getJSONObject(0).getString("id");
                        if(!memberId.equals(uid())){
                            JSONObject m=new JSONObject();m.put("conversation_id",cid);m.put("user_id",memberId);m.put("role","member");
                            SupabaseClient.post("/rest/v1/conversation_members",token(),m);
                        }
                    }
                }
                runOnUiThread(()->openChat(new ChatItem(cid,title,true)));
            }catch(Exception e){runOnUiThread(()->message("Group creation failed: "+e.getMessage()));}
        });
    }
    private void showGroupManagement() {
        stopRealtime();
        inChat=false;handler.removeCallbacks(refreshRunnable);
        final String cid=currentConversationId;
        base("Group settings","Back",v->openChat(new ChatItem(cid,currentConversationTitle,true)));
        EditText title=field("Group name",InputType.TYPE_CLASS_TEXT);
        Button rename=button("Save group name");
        content.addView(title,match(dp(52)));content.addView(rename,match(dp(48)));
        EditText addNames=field("Add usernames, separated by commas",InputType.TYPE_CLASS_TEXT);
        Button add=button("Add members");
        content.addView(addNames,match(dp(52)));content.addView(add,match(dp(48)));
        TextView memberHeading=text("Members",16,Color.WHITE,true);
        LinearLayout.LayoutParams mh=match(dp(38));mh.topMargin=dp(14);content.addView(memberHeading,mh);
        LinearLayout memberList=new LinearLayout(this);memberList.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.addView(memberList);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Button leave=button("Leave group");content.addView(leave,match(dp(48)));
        final boolean[] owner={false};
        io.execute(()->{try{
            JSONArray conv=SupabaseClient.getArray("/rest/v1/conversations?select=title,created_by&id=eq."+SupabaseClient.enc(cid)+"&limit=1",token());
            if(conv.length()>0){JSONObject row=conv.getJSONObject(0);owner[0]=uid().equals(row.optString("created_by"));runOnUiThread(()->title.setText(row.optString("title","")));}
            JSONArray members=SupabaseClient.getArray("/rest/v1/conversation_members?select=user_id,role&conversation_id=eq."+SupabaseClient.enc(cid),token());
            runOnUiThread(()->{
                memberList.removeAllViews();
                for(int i=0;i<members.length();i++){
                    JSONObject member=members.optJSONObject(i);if(member==null)continue;
                    String memberId=member.optString("user_id"),role=member.optString("role","member");
                    io.execute(()->{try{
                        JSONArray profiles=SupabaseClient.getArray("/rest/v1/profiles?select=display_name,username&id=eq."+SupabaseClient.enc(memberId)+"&limit=1",token());
                        String label=memberId.equals(uid())?"You":memberId;
                        if(profiles.length()>0){JSONObject profile=profiles.getJSONObject(0);label=profile.optString("display_name",profile.optString("username",label));if(!profile.optString("username","").isEmpty())label+="  @"+profile.optString("username");}
                        final String display=label+"  ·  "+role;
                        runOnUiThread(()->{
                            Button row=button(display);row.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
                            LinearLayout.LayoutParams rp=match(dp(48));rp.bottomMargin=dp(5);memberList.addView(row,rp);
                            if(owner[0]&&!memberId.equals(uid()))row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Remove member?").setMessage(display).setPositiveButton("Remove",(d,w)->io.execute(()->{try{SupabaseClient.delete("/rest/v1/conversation_members?conversation_id=eq."+SupabaseClient.enc(cid)+"&user_id=eq."+SupabaseClient.enc(memberId),token());runOnUiThread(()->{message("Member removed.");showGroupManagement();});}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}})).setNegativeButton("Cancel",null).show();return true;});
                        });
                    }catch(Exception ignored){}});
                }
            });
            if(!owner[0])runOnUiThread(()->{rename.setEnabled(false);add.setEnabled(false);});
        }catch(Exception e){runOnUiThread(()->message("Group details: "+e.getMessage()));}});
        rename.setOnClickListener(v->{if(!owner[0]){message("Only the group owner can rename this group.");return;}io.execute(()->{try{JSONObject p=new JSONObject();p.put("title",title.getText().toString().trim());SupabaseClient.patch("/rest/v1/conversations?id=eq."+SupabaseClient.enc(cid),token(),p);currentConversationTitle=title.getText().toString().trim();runOnUiThread(()->message("Group name updated."));}catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});});
        add.setOnClickListener(v->{if(!owner[0]){message("Only the group owner can add members.");return;}String[] handles=addNames.getText().toString().split(",");io.execute(()->{int added=0;try{for(String raw:handles){String h=raw.trim().replace("@","");if(h.isEmpty())continue;JSONArray found=SupabaseClient.getArray("/rest/v1/profiles?select=id&username=eq."+SupabaseClient.enc(h)+"&limit=1",token());if(found.length()==0)continue;String id=found.getJSONObject(0).optString("id");if(id.equals(uid()))continue;JSONArray existing=SupabaseClient.getArray("/rest/v1/conversation_members?select=user_id&conversation_id=eq."+SupabaseClient.enc(cid)+"&user_id=eq."+SupabaseClient.enc(id),token());if(existing.length()>0)continue;JSONObject m=new JSONObject();m.put("conversation_id",cid);m.put("user_id",id);m.put("role","member");SupabaseClient.post("/rest/v1/conversation_members",token(),m);added++;}final int count=added;runOnUiThread(()->{message("Added "+count+" member(s).");showGroupManagement();});}catch(Exception e){runOnUiThread(()->message("Could not add members: "+e.getMessage()));}});});
        leave.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Leave group?").setMessage("You will no longer see this group.").setPositiveButton("Leave",(d,w)->io.execute(()->{try{
            if(owner[0]){
                JSONArray remaining=SupabaseClient.getArray("/rest/v1/conversation_members?select=user_id&conversation_id=eq."+SupabaseClient.enc(cid)+"&user_id=neq."+SupabaseClient.enc(uid()),token());
                if(remaining.length()>0){runOnUiThread(()->message("As group owner, remove all other members before leaving. This prevents an ownerless group."));return;}
            }
            SupabaseClient.delete("/rest/v1/conversation_members?conversation_id=eq."+SupabaseClient.enc(cid)+"&user_id=eq."+SupabaseClient.enc(uid()),token());
            runOnUiThread(()->{inChat=false;showHome();});
        }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}})).setNegativeButton("Cancel",null).show());
    }
    private void showProfile() {
        stopRealtime();
        inChat=false;handler.removeCallbacks(refreshRunnable);
        base("Profile","Back",v->showHome());
        content.addView(text("Signed in as  "+ChatSession.email(this),13,0xFFAAB6C8,false));
        EditText display=field("Display name",InputType.TYPE_CLASS_TEXT);
        EditText username=field("Username",InputType.TYPE_CLASS_TEXT);
        EditText bio=field("About you",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        content.addView(display,match(dp(54)));content.addView(username,match(dp(54)));content.addView(bio,match(dp(54)));
        Button save=button("Save profile");LinearLayout.LayoutParams sp=match(dp(50));sp.topMargin=dp(12);content.addView(save,sp);
        Button language=button(isArabic()?"اللغة: العربية":"Language: English");
        LinearLayout.LayoutParams langParams=match(dp(48));langParams.topMargin=dp(10);content.addView(language,langParams);
        language.setOnClickListener(v->{getSharedPreferences("app_settings",MODE_PRIVATE).edit().putString("language",isArabic()?"en":"ar").apply();showProfile();});
        Button logout=button("Sign out");LinearLayout.LayoutParams p=match(dp(52));p.topMargin=dp(10);content.addView(logout,p);
        io.execute(()->{
            try{
                JSONArray rows=SupabaseClient.getArray("/rest/v1/profiles?select=display_name,username,bio&id=eq."+SupabaseClient.enc(uid())+"&limit=1",token());
                if(rows.length()>0){JSONObject me=rows.getJSONObject(0);runOnUiThread(()->{display.setText(me.optString("display_name",""));username.setText(me.optString("username",""));bio.setText(me.optString("bio",""));});}
            }catch(Exception e){runOnUiThread(()->message("Profile: "+e.getMessage()));}
        });
        save.setOnClickListener(v->{
            String handle=username.getText().toString().trim().replace("@","");
            if(handle.length()<3){message("Username must contain at least 3 characters.");return;}
            io.execute(()->{try{
                JSONObject patch=new JSONObject();patch.put("display_name",display.getText().toString().trim());patch.put("username",handle);patch.put("bio",bio.getText().toString().trim());
                SupabaseClient.patch("/rest/v1/profiles?id=eq."+SupabaseClient.enc(uid()),token(),patch);
                runOnUiThread(()->message("Profile saved."));
            }catch(Exception e){runOnUiThread(()->message("Could not save profile: "+e.getMessage()));}});
        });
        logout.setOnClickListener(v->{
            WorkManager.getInstance(this).cancelUniqueWork("chat-message-check");
            final String userId=ChatSession.userId(this);
            io.execute(()->{
                try{
                    String access=ChatSession.validToken(getApplicationContext());
                    if(!userId.isEmpty()&&!access.isEmpty()){
                        JSONObject offline=new JSONObject();offline.put("is_online",false);offline.put("last_seen_at",nowIso());
                        SupabaseClient.patch("/rest/v1/profiles?id=eq."+SupabaseClient.enc(userId),access,offline);
                    }
                }catch(Exception ignored){}
                ChatSession.clear(getApplicationContext());
                getSharedPreferences("chat_notifications",MODE_PRIVATE).edit().clear().apply();
                runOnUiThread(()->{stopRealtime();showAuth();});
            });
        });
    }
    private void showMessageActions(JSONObject msg) {
        String id=msg.optString("id"), original=msg.optString("body","");
        new AlertDialog.Builder(this).setTitle("Your message")
            .setItems(new String[]{"Edit message","Delete message"},(dialog,which)->{
                if(which==0){
                    EditText editor=field("Message",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setText(original);
                    new AlertDialog.Builder(this).setTitle("Edit message").setView(editor)
                        .setPositiveButton("Save",(d,w)->io.execute(()->{try{JSONObject patch=new JSONObject();patch.put("body",editor.getText().toString().trim());patch.put("edited_at",nowIso());SupabaseClient.patch("/rest/v1/messages?id=eq."+SupabaseClient.enc(id),token(),patch);runOnUiThread(()->loadMessages(false));}catch(Exception e){runOnUiThread(()->message("Edit failed: "+e.getMessage()));}}))
                        .setNegativeButton("Cancel",null).show();
                }else{
                    new AlertDialog.Builder(this).setTitle("Delete message?").setMessage("This message will be removed from the chat.")
                        .setPositiveButton("Delete",(d,w)->io.execute(()->{try{JSONObject patch=new JSONObject();patch.put("deleted_at",nowIso());SupabaseClient.patch("/rest/v1/messages?id=eq."+SupabaseClient.enc(id),token(),patch);runOnUiThread(()->loadMessages(false));}catch(Exception e){runOnUiThread(()->message("Delete failed: "+e.getMessage()));}}))
                        .setNegativeButton("Cancel",null).show();
                }
            }).show();
    }
    private void showUserActions(String userId,String name) {
        io.execute(()->{try{
            JSONArray rows=SupabaseClient.getArray("/rest/v1/user_blocks?select=blocked_id&blocker_id=eq."+SupabaseClient.enc(uid())+"&blocked_id=eq."+SupabaseClient.enc(userId),token());
            boolean blocked=rows.length()>0;
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle(name)
                .setItems(blocked?new String[]{"Open chat","Unblock user"}:new String[]{"Open chat","Block user"},(d,w)->{
                    if(w==0)startDirect(userId,name);
                    else io.execute(()->{try{
                        if(blocked)SupabaseClient.delete("/rest/v1/user_blocks?blocker_id=eq."+SupabaseClient.enc(uid())+"&blocked_id=eq."+SupabaseClient.enc(userId),token());
                        else {JSONObject row=new JSONObject();row.put("blocker_id",uid());row.put("blocked_id",userId);SupabaseClient.post("/rest/v1/user_blocks",token(),row);}
                        runOnUiThread(()->message(blocked?"User unblocked.":"User blocked."));
                    }catch(Exception e){runOnUiThread(()->message("Block action failed: "+e.getMessage()));}});
                }).show());
        }catch(Exception e){runOnUiThread(()->message(e.getMessage()));}});
    }
    private void setPresence(boolean online) {
        final String userId=ChatSession.userId(this);
        if(userId.isEmpty())return;
        io.execute(()->{try{
            String access=ChatSession.validToken(getApplicationContext());
            if(access.isEmpty())return;
            if(realtime!=null)realtime.updateAccessToken(access);
            JSONObject p=new JSONObject();p.put("is_online",online);p.put("last_seen_at",nowIso());
            SupabaseClient.patch("/rest/v1/profiles?id=eq."+SupabaseClient.enc(userId),access,p);
        }catch(Exception ignored){}});
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(handleAuthRedirect(intent))return;}
    @Override protected void onStart(){super.onStart();activityStarted=true;setPresence(true);handler.removeCallbacks(presenceHeartbeat);handler.postDelayed(presenceHeartbeat,60000L);if(inChat&&!currentConversationId.isEmpty()){startRealtime(currentConversationId);handler.removeCallbacks(refreshRunnable);handler.postDelayed(refreshRunnable,5000);}}
    @Override protected void onStop(){activityStarted=false;handler.removeCallbacks(presenceHeartbeat);if(inChat&&!currentConversationId.isEmpty())setTypingStatus(currentConversationId,false);stopRealtime();setPresence(false);super.onStop();}
    @Override protected void onDestroy(){stopRealtime();handler.removeCallbacks(refreshRunnable);io.shutdown();super.onDestroy();}
}
