package com.chatapp.mobile;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommunityActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable channelPoll;
    private String activeServerChannelId = "";
    private Runnable activeChannelRefresh;
    private SupabaseRealtime serverRealtime;
    private LinearLayout root, body;
    private String currentChannelId = "", currentChannelName = "", currentGroupId = "";
    private boolean currentChannelOwner = false;
    private static final int PICK_STATUS_MEDIA = 711;

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String uid(){return ChatSession.userId(this);}
    private String token(){return ChatSession.validToken(this);}
    private String enc(String s)throws Exception{return SupabaseClient.enc(s);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
    private TextView label(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String s){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF8B5CF6,0xFF6D45D8});
        d.setCornerRadius(dp(15));b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x44FFFFFF),d,null));b.setMinHeight(dp(44));return b;
    }
    private EditText field(String hint,int type){
        EditText e=new EditText(this);e.setSingleLine(false);e.setHint(hint);e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF9BA3B8);e.setTextSize(14);e.setInputType(type);e.setPadding(dp(13),dp(8),dp(13),dp(8));
        GradientDrawable d=new GradientDrawable();d.setColor(0xFF151827);d.setCornerRadius(dp(13));d.setStroke(dp(1),0xFF2A3044);e.setBackground(d);return e;
    }
    private LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(-1,h);}
    private void base(String title){
        if(channelPoll!=null)handler.removeCallbacks(channelPoll);
        if(serverRealtime!=null){serverRealtime.stop();serverRealtime=null;}
        channelPoll=null;activeServerChannelId="";activeChannelRefresh=null;
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setBackgroundColor(0xFF0B0D16);setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView heading=label(title,20,true);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button back=button("Back");bar.addView(back, new LinearLayout.LayoutParams(dp(82),dp(42)));back.setOnClickListener(v->home());
        root.addView(bar,full(dp(52)));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void home(){
        base("NEXORA · Community");
        addNav("Statuses","Share a photo, video or text that expires after 24 hours",this::statuses);
        addNav("Channels","Discover, follow and publish channel updates",this::channels);
        addNav("Servers","Create communities with text channels",this::servers);
        addNav("Groups","Manage members, roles, invites and polls",this::groups);
        addNav("Privacy","Control last-seen, photo and status visibility",this::privacy);
        addNav("Join a group","Enter an invite code",this::joinGroup);
    }
    private void addNav(String title,String sub,Runnable action){
        Button b=button(title+"  ›");b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(54));p.topMargin=dp(9);body.addView(b,p);
        TextView t=label(sub,12,false);t.setTextColor(0xFF9BA3B8);LinearLayout.LayoutParams q=full(dp(34));q.leftMargin=dp(8);body.addView(t,q);b.setOnClickListener(v->action.run());
    }
    @Override protected void onCreate(Bundle b){super.onCreate(b);if(uid().isEmpty()){finish();return;}home();}

    private void startServerRealtime(){
        if(activeServerChannelId.isEmpty()||isFinishing())return;
        if(serverRealtime!=null)serverRealtime.stop();
        serverRealtime=new SupabaseRealtime();
        String channelId=activeServerChannelId;
        serverRealtime.startServerChannel(this,channelId,token(),new SupabaseRealtime.Listener(){
            @Override public void onMessageChange(){
                if(channelId.equals(activeServerChannelId)&&activeChannelRefresh!=null)activeChannelRefresh.run();
            }
            @Override public void onState(boolean connected,String detail){}
        });
    }

    @Override protected void onPause(){if(channelPoll!=null)handler.removeCallbacks(channelPoll);if(serverRealtime!=null)serverRealtime.stop();super.onPause();}
    @Override protected void onResume(){super.onResume();if(!activeServerChannelId.isEmpty())startServerRealtime();if(channelPoll!=null)handler.postDelayed(channelPoll,1500L);}
    private void statuses(){
        base("Statuses");
        EditText text=field("Write a text status…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.addView(text,full(dp(64)));
        Button publish=button("Publish text status"),media=button("Add photo or video");
        LinearLayout.LayoutParams p=full(dp(46));p.topMargin=dp(7);body.addView(publish,p);p=full(dp(46));p.topMargin=dp(6);body.addView(media,p);
        TextView hint=label("Statuses expire automatically after 24 hours.",12,false);hint.setTextColor(0xFF9BA3B8);body.addView(hint,full(dp(34)));
        ScrollView scroll=new ScrollView(this);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        publish.setOnClickListener(v->{String value=text.getText().toString().trim();if(value.isEmpty()){toast("Write a status first.");return;}io.execute(()->{try{JSONObject r=new JSONObject();r.put("user_id",uid());r.put("kind","text");r.put("body",value);r.put("expires_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new java.util.Date(System.currentTimeMillis()+86400000L)));SupabaseClient.post("/rest/v1/statuses",token(),r);runOnUiThread(()->{toast("Status published.");statuses();});}catch(Exception e){toast("Publish failed: "+e.getMessage());}});});
        media.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp","image/gif","video/mp4"});startActivityForResult(i,PICK_STATUS_MEDIA);});
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/statuses?select=id,user_id,kind,body,media_path,created_at,expires_at&expires_at=gt."+enc(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new java.util.Date()))+"&order=created_at.desc&limit=80",token());runOnUiThread(()->{list.removeAllViews();if(rows.length()==0){list.addView(label("No active statuses.",14,false));return;}for(int n=0;n<rows.length();n++){JSONObject r=rows.optJSONObject(n);if(r==null)continue;String id=r.optString("id"),owner=r.optString("user_id"),kind=r.optString("kind"),bodyText=r.optString("body","");Button item=button((uid().equals(owner)?"Your status · ":"Status · ")+(kind.equals("text")?bodyText:kind.toUpperCase(Locale.ROOT)));item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ip=full(dp(52));ip.topMargin=dp(5);list.addView(item,ip);item.setOnClickListener(v->{if(uid().equals(owner)){new AlertDialog.Builder(this).setItems(new String[]{"View count","Delete status"},(d,w)->{if(w==0)io.execute(()->{try{JSONArray views=SupabaseClient.getArray("/rest/v1/status_views?select=viewer_id&status_id=eq."+enc(id),token());toast("Views: "+views.length());}catch(Exception e){toast(e.getMessage());}});else io.execute(()->{try{SupabaseClient.delete("/rest/v1/statuses?id=eq."+enc(id),token());runOnUiThread(this::statuses);}catch(Exception e){toast(e.getMessage());}});}).show();}else if(kind.equals("text"))new AlertDialog.Builder(this).setMessage(bodyText).setPositiveButton("Close",null).show();else openMedia(r.optString("media_path"));});}});}catch(Exception e){toast("Statuses: "+e.getMessage());}});
    }
    private void openMedia(String path){io.execute(()->{try{String url=SupabaseStorage.signedUrl(path,token());Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));runOnUiThread(()->{try{startActivity(i);}catch(Exception e){toast("No app can open this media.");}});}catch(Exception e){toast(e.getMessage());}});}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_STATUS_MEDIA&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();io.execute(()->{try{String uploaded=SupabaseStorage.upload(this,uri,"statuses/"+uid(),token());String[] parts=uploaded.split("\\|",2);JSONObject r=new JSONObject();r.put("user_id",uid());r.put("kind",parts.length>1?parts[1]:"image");r.put("media_path",parts[0]);r.put("expires_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new java.util.Date(System.currentTimeMillis()+86400000L)));SupabaseClient.post("/rest/v1/statuses",token(),r);runOnUiThread(()->{toast("Media status published.");statuses();});}catch(Exception e){toast("Upload failed: "+e.getMessage());}});}}

    private void channels(){
        base("Channels");Button create=button("Create a channel");body.addView(create,full(dp(46)));
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        create.setOnClickListener(v->{LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText name=field("Channel name",InputType.TYPE_CLASS_TEXT),desc=field("Description",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);box.addView(name,full(dp(52)));box.addView(desc,full(dp(68)));new AlertDialog.Builder(this).setTitle("Create channel").setView(box).setPositiveButton("Create",(d,w)->io.execute(()->{try{JSONObject r=new JSONObject();r.put("owner_id",uid());r.put("name",name.getText().toString().trim());r.put("description",desc.getText().toString().trim());r.put("is_public",true);JSONArray made=SupabaseClient.post("/rest/v1/channels",token(),r);String id=made.getJSONObject(0).getString("id");runOnUiThread(()->openChannel(id,name.getText().toString(),true));}catch(Exception e){toast("Create channel failed: "+e.getMessage());}})).setNegativeButton("Cancel",null).show();});
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/channels?select=id,name,description,owner_id,channel_members(user_id,role)&order=created_at.desc&limit=100",token());runOnUiThread(()->{list.removeAllViews();for(int n=0;n<rows.length();n++){JSONObject c=rows.optJSONObject(n);if(c==null)continue;String id=c.optString("id"),name=c.optString("name"),owner=c.optString("owner_id");boolean own=uid().equals(owner);JSONArray members=c.optJSONArray("channel_members");boolean following=own;if(members!=null)for(int j=0;j<members.length();j++){JSONObject m=members.optJSONObject(j);if(m!=null&&uid().equals(m.optString("user_id")))following=true;}Button b=button(name+(following?" · Following":""));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(52));p.topMargin=dp(6);list.addView(b,p);b.setOnClickListener(v->openChannel(id,name,own));}});}catch(Exception e){toast("Channels: "+e.getMessage());}});
    }
    private void openChannel(String id,String name,boolean own){
        currentChannelId=id;currentChannelName=name;currentChannelOwner=own;base(name);
        EditText post=field("Write a channel update…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.addView(post,full(dp(58)));
        Button publish=button("Publish post"),follow=button(own?"Channel owner":"Follow / unfollow");body.addView(publish,full(dp(44)));LinearLayout.LayoutParams fp=full(dp(44));fp.topMargin=dp(5);body.addView(follow,fp);publish.setEnabled(own);
        publish.setOnClickListener(v->{String value=post.getText().toString().trim();if(value.isEmpty())return;io.execute(()->{try{JSONObject r=new JSONObject();r.put("channel_id",id);r.put("author_id",uid());r.put("body",value);SupabaseClient.post("/rest/v1/channel_posts",token(),r);runOnUiThread(()->{toast("Post published.");openChannel(id,name,own);});}catch(Exception e){toast(e.getMessage());}});});
        follow.setOnClickListener(v->{if(own)return;io.execute(()->{try{JSONArray old=SupabaseClient.getArray("/rest/v1/channel_members?select=role&channel_id=eq."+enc(id)+"&user_id=eq."+enc(uid()),token());if(old.length()>0)SupabaseClient.delete("/rest/v1/channel_members?channel_id=eq."+enc(id)+"&user_id=eq."+enc(uid()),token());else{JSONObject r=new JSONObject();r.put("channel_id",id);r.put("user_id",uid());r.put("role","follower");SupabaseClient.post("/rest/v1/channel_members",token(),r);}runOnUiThread(()->openChannel(id,name,false));}catch(Exception e){toast("Follow action failed: "+e.getMessage());}});});
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/channel_posts?select=id,body,created_at,channel_post_reactions(emoji,user_id)&channel_id=eq."+enc(id)+"&order=created_at.desc&limit=100",token());runOnUiThread(()->{list.removeAllViews();for(int n=0;n<rows.length();n++){JSONObject r=rows.optJSONObject(n);if(r==null)continue;TextView t=label(r.optString("body",""),15,false);t.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF20263A);bg.setCornerRadius(dp(14));t.setBackground(bg);LinearLayout.LayoutParams p=full(-2);p.topMargin=dp(6);list.addView(t,p);}});}catch(Exception e){toast("Posts: "+e.getMessage());}});
    }


    // NEXORA server directory and text-channel workflow.
    private void servers(){
        base("NEXORA Servers");
        Button create=button("Create a server"),joinInvite=button("Join with invite code");
        body.addView(create,full(dp(46)));LinearLayout.LayoutParams jip=full(dp(44));jip.topMargin=dp(5);body.addView(joinInvite,jip);
        joinInvite.setOnClickListener(v->{EditText code=field("Server invite code",InputType.TYPE_CLASS_TEXT);new AlertDialog.Builder(this).setTitle("Join server").setView(code).setPositiveButton("Join",(d,w)->io.execute(()->{try{JSONObject args=new JSONObject();args.put("p_invite_code",code.getText().toString().trim());SupabaseClient.request("POST","/rest/v1/rpc/accept_server_invite",token(),args,null);runOnUiThread(()->{toast("Joined server.");servers();});}catch(Exception e){toast("Invite failed: "+e.getMessage());}})).setNegativeButton("Cancel",null).show();});
        ScrollView sc=new ScrollView(this);
        body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        create.setOnClickListener(v->{
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            EditText name=field("Server name",InputType.TYPE_CLASS_TEXT);
            EditText description=field("Description",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            box.addView(name,full(dp(52)));box.addView(description,full(dp(68)));
            new AlertDialog.Builder(this).setTitle("Create a server").setView(box)
                .setPositiveButton("Create",(d,w)->io.execute(()->{
                    try{
                        String serverName=name.getText().toString().trim();
                        if(serverName.length()<2)throw new Exception("Server name must contain at least 2 characters.");
                        JSONObject row=new JSONObject();row.put("owner_id",uid());row.put("name",serverName);
                        row.put("description",description.getText().toString().trim());row.put("is_public",true);
                        JSONArray made=SupabaseClient.post("/rest/v1/servers",token(),row);
                        String id=made.getJSONObject(0).getString("id");
                        runOnUiThread(()->openServer(id,serverName,true));
                    }catch(Exception e){toast("Server creation failed: "+e.getMessage());}
                })).setNegativeButton("Cancel",null).show();
        });
        io.execute(()->{
            try{
                JSONArray rows=SupabaseClient.getArray("/rest/v1/servers?select=id,name,description,owner_id,is_public,server_members(user_id,role)&order=created_at.desc&limit=100",token());
                runOnUiThread(()->{
                    list.removeAllViews();
                    if(rows.length()==0)list.addView(label("No servers yet. Create the first one.",14,false));
                    for(int n=0;n<rows.length();n++){
                        JSONObject s=rows.optJSONObject(n);if(s==null)continue;
                        String id=s.optString("id"),name=s.optString("name","Server");
                        boolean owner=uid().equals(s.optString("owner_id"));
                        boolean manager=owner;
                        JSONArray members=s.optJSONArray("server_members");boolean joined=owner;
                        if(members!=null)for(int j=0;j<members.length();j++){
                            JSONObject m=members.optJSONObject(j);
                            if(m!=null&&uid().equals(m.optString("user_id"))){joined=true;String memberRole=m.optString("role","member");if("owner".equals(memberRole)||"admin".equals(memberRole))manager=true;}
                        }
                        Button item=button("◈  "+name+(joined?" · Joined":(s.optBoolean("is_public")?" · Public":" · Private")));
                        item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams p=full(dp(54));p.topMargin=dp(7);list.addView(item,p);
                        final boolean canManage=manager;
                        item.setOnClickListener(v->openServer(id,name,canManage));
                    }
                });
            }catch(Exception e){toast("Servers: "+e.getMessage());}
        });
    }

    private void openServer(String serverId,String serverName,boolean owner){
        base(serverName);
        TextView description=label("Server channels and community messages",12,false);
        description.setTextColor(0xFF9BA3B8);body.addView(description,full(dp(30)));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button join=button("Join server"),create=button("Create channel"),invite=button("Invite"),members=button("Members");
        actions.addView(join,new LinearLayout.LayoutParams(0,dp(44),1));
        actions.addView(create,new LinearLayout.LayoutParams(0,dp(44),1));
        body.addView(actions,full(dp(48)));create.setVisibility(owner?View.VISIBLE:View.GONE);
        LinearLayout managerActions=new LinearLayout(this);managerActions.setOrientation(LinearLayout.HORIZONTAL);
        managerActions.addView(invite,new LinearLayout.LayoutParams(0,dp(42),1));managerActions.addView(members,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams map=full(dp(46));map.topMargin=dp(4);body.addView(managerActions,map);
        invite.setVisibility(owner?View.VISIBLE:View.GONE);members.setVisibility(owner?View.VISIBLE:View.GONE);
        invite.setOnClickListener(v->io.execute(()->{try{JSONObject row=new JSONObject();row.put("server_id",serverId);row.put("created_by",uid());JSONArray made=SupabaseClient.post("/rest/v1/server_invites",token(),row);String code=made.getJSONObject(0).optString("invite_code");runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Server invite code").setMessage(code).setPositiveButton("Copy",(d,w)->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("NEXORA server invite",code));toast("Invite copied.");}).setNegativeButton("Close",null).show());}catch(Exception e){toast("Invite creation failed: "+e.getMessage());}}));
        members.setOnClickListener(v->manageServerMembers(serverId,serverName));
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        final boolean[] member={owner};
        io.execute(()->{
            try{
                JSONArray membership=SupabaseClient.getArray("/rest/v1/server_members?select=role,is_banned&server_id=eq."+enc(serverId)+"&user_id=eq."+enc(uid()),token());
                if(membership.length()>0&&!membership.getJSONObject(0).optBoolean("is_banned"))member[0]=true;
                runOnUiThread(()->{
                    join.setVisibility(member[0]?View.GONE:View.VISIBLE);
                    create.setVisibility(owner?View.VISIBLE:View.GONE);
                    if(member[0])loadServerChannels(serverId,serverName,list);
                    else list.addView(label("Join this public server to see its channels.",14,false));
                });
            }catch(Exception e){toast("Server membership: "+e.getMessage());}
        });
        join.setOnClickListener(v->io.execute(()->{
            try{
                JSONObject row=new JSONObject();row.put("server_id",serverId);row.put("user_id",uid());row.put("role","member");
                SupabaseClient.post("/rest/v1/server_members",token(),row);
                runOnUiThread(()->{member[0]=true;join.setVisibility(View.GONE);loadServerChannels(serverId,serverName,list);});
            }catch(Exception e){toast("Could not join server: "+e.getMessage());}
        }));
        create.setOnClickListener(v->{
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
            EditText name=field("Channel name",InputType.TYPE_CLASS_TEXT),topic=field("Channel topic (optional)",InputType.TYPE_CLASS_TEXT);
            box.addView(name,full(dp(52)));box.addView(topic,full(dp(52)));
            new AlertDialog.Builder(this).setTitle("Create text channel").setView(box)
                .setPositiveButton("Create",(d,w)->io.execute(()->{
                    try{
                        String channelName=name.getText().toString().trim().replace(" ","-").toLowerCase(Locale.ROOT);
                        if(channelName.isEmpty())throw new Exception("Enter a channel name.");
                        JSONObject row=new JSONObject();row.put("server_id",serverId);row.put("name",channelName);
                        row.put("topic",topic.getText().toString().trim());row.put("channel_type","text");row.put("created_by",uid());
                        SupabaseClient.post("/rest/v1/server_channels",token(),row);
                        runOnUiThread(()->loadServerChannels(serverId,serverName,list));
                    }catch(Exception e){toast("Channel creation failed: "+e.getMessage());}
                })).setNegativeButton("Cancel",null).show();
        });
    }


    private void manageServerMembers(String serverId,String serverName){
        base(serverName+" · Members");
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        io.execute(()->{
            try{
                JSONArray rows=SupabaseClient.getArray("/rest/v1/server_members?select=user_id,role,is_muted,is_banned&server_id=eq."+enc(serverId)+"&order=joined_at.asc",token());
                runOnUiThread(()->{
                    list.removeAllViews();
                    for(int n=0;n<rows.length();n++){
                        JSONObject m=rows.optJSONObject(n);if(m==null)continue;
                        String user=m.optString("user_id"),role=m.optString("role","member");
                        Button item=button(user+" · "+role+(m.optBoolean("is_muted")?" · muted":"")+(m.optBoolean("is_banned")?" · banned":""));
                        item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(48));p.topMargin=dp(5);list.addView(item,p);
                        if(!user.equals(uid())&&!role.equals("owner"))item.setOnClickListener(v->new AlertDialog.Builder(this)
                            .setTitle("Member controls")
                            .setItems(new String[]{"Make admin","Make moderator","Make member","Mute / unmute","Ban / unban"},(d,w)->{
                                if(w<3){String[] roles={"admin","moderator","member"};setServerRole(serverId,user,roles[w]);}
                                else setServerRestriction(serverId,user,w==3?"mute":"ban",w==3?m.optBoolean("is_muted"):m.optBoolean("is_banned"));
                            }).show());
                    }
                });
            }catch(Exception e){toast("Members: "+e.getMessage());}
        });
    }

    private void setServerRole(String serverId,String userId,String role){
        io.execute(()->{try{JSONObject args=new JSONObject();args.put("p_server_id",serverId);args.put("p_user_id",userId);args.put("p_role",role);SupabaseClient.request("POST","/rest/v1/rpc/set_server_member_role",token(),args,null);runOnUiThread(()->{toast("Role updated.");manageServerMembers(serverId,"Server");});}catch(Exception e){toast("Role change failed: "+e.getMessage());}});
    }

    private void setServerRestriction(String serverId,String userId,String action,boolean wasEnabled){
        io.execute(()->{try{JSONObject args=new JSONObject();args.put("p_server_id",serverId);args.put("p_user_id",userId);args.put("p_action",action);args.put("p_enabled",!wasEnabled);SupabaseClient.request("POST","/rest/v1/rpc/set_server_member_restriction",token(),args,null);runOnUiThread(()->{toast("Member restriction updated.");manageServerMembers(serverId,"Server");});}catch(Exception e){toast("Moderation failed: "+e.getMessage());}});
    }

    private void loadServerChannels(String serverId,String serverName,LinearLayout list){
        io.execute(()->{
            try{
                JSONArray channels=SupabaseClient.getArray("/rest/v1/server_channels?select=id,name,topic,channel_type,position&server_id=eq."+enc(serverId)+"&order=position.asc,created_at.asc",token());
                runOnUiThread(()->{
                    list.removeAllViews();
                    if(channels.length()==0)list.addView(label("No channels yet. A server manager can create one.",13,false));
                    for(int n=0;n<channels.length();n++){
                        JSONObject ch=channels.optJSONObject(n);if(ch==null)continue;
                        String id=ch.optString("id"),name=ch.optString("name");
                        Button b=button("#  "+name);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams p=full(dp(48));p.topMargin=dp(5);list.addView(b,p);
                        b.setOnClickListener(v->openServerChannel(serverId,serverName,id,name));
                    }
                });
            }catch(Exception e){toast("Channels: "+e.getMessage());}
        });
    }

    private void openServerChannel(String serverId,String serverName,String channelId,String channelName){
        base(serverName+"  /  #"+channelName);
        TextView hint=label("Messages in this server channel",12,false);hint.setTextColor(0xFF9BA3B8);body.addView(hint,full(dp(28)));
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);sc.addView(messages);
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);
        EditText input=field("Message #"+channelName,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        Button send=button("Send");composer.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        composer.addView(send,new LinearLayout.LayoutParams(dp(82),dp(50)));body.addView(composer,full(dp(58)));
        Runnable refresh=()->io.execute(()->{
            try{
                JSONArray rows=SupabaseClient.getArray("/rest/v1/server_channel_messages?select=id,author_id,content,created_at,edited_at,deleted_at&channel_id=eq."+enc(channelId)+"&order=created_at.asc&limit=150",token());
                runOnUiThread(()->{
                    messages.removeAllViews();
                    for(int n=0;n<rows.length();n++){
                        JSONObject r=rows.optJSONObject(n);if(r==null)continue;
                        String author=r.optString("author_id"),messageId=r.optString("id");
                        String content=r.optString("deleted_at").isEmpty()?r.optString("content"):"[message deleted]";
                        TextView line=label((author.equals(uid())?"You":"Member "+author.substring(0,Math.min(6,author.length())))+"\\n"+content,14,false);
                        line.setPadding(dp(12),dp(10),dp(12),dp(10));
                        GradientDrawable bg=new GradientDrawable();bg.setColor(author.equals(uid())?0xFF6D45D8:0xFF20263A);bg.setCornerRadius(dp(12));line.setBackground(bg);
                        LinearLayout.LayoutParams p=full(-2);p.topMargin=dp(5);messages.addView(line,p);
                        if(author.equals(uid()))line.setOnLongClickListener(v->{
                            new AlertDialog.Builder(this).setItems(new String[]{"Delete message"},(d,w)->io.execute(()->{
                                try{JSONObject patch=new JSONObject();patch.put("deleted_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new java.util.Date()));SupabaseClient.request("PATCH","/rest/v1/server_channel_messages?id=eq."+enc(messageId),token(),patch,null);runOnUiThread(()->toast("Message deleted."));}
                                catch(Exception e){toast("Delete failed: "+e.getMessage());}
                            })).show();return true;
                        });
                    }
                    sc.post(()->sc.fullScroll(View.FOCUS_DOWN));
                });
            }catch(Exception e){toast("Messages: "+e.getMessage());}
        });
        activeServerChannelId=channelId;
        activeChannelRefresh=refresh;
        channelPoll=new Runnable(){@Override public void run(){if(activeServerChannelId.equals(channelId)&&!isFinishing()){refresh.run();handler.postDelayed(this,5000L);}}};
        startServerRealtime();
        refresh.run();handler.postDelayed(channelPoll,5000L);
        send.setOnClickListener(v->{
            String value=input.getText().toString().trim();if(value.isEmpty())return;
            send.setEnabled(false);
            io.execute(()->{
                try{
                    JSONObject row=new JSONObject();row.put("server_id",serverId);row.put("channel_id",channelId);row.put("author_id",uid());row.put("content",value);
                    SupabaseClient.post("/rest/v1/server_channel_messages",token(),row);
                    runOnUiThread(()->{input.setText("");send.setEnabled(true);refresh.run();});
                }catch(Exception e){runOnUiThread(()->send.setEnabled(true));toast("Send failed: "+e.getMessage());}
            });
        });
    }

    private void groups(){
        base("Groups");Button join=button("Join with invite code");body.addView(join,full(dp(46)));join.setOnClickListener(v->joinGroup());
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/conversation_members?select=conversation_id,conversations(id,title,is_group)&user_id=eq."+enc(uid()),token());runOnUiThread(()->{list.removeAllViews();for(int n=0;n<rows.length();n++){JSONObject r=rows.optJSONObject(n),c=r==null?null:r.optJSONObject("conversations");if(c==null||!c.optBoolean("is_group"))continue;String id=c.optString("id"),name=c.optString("title","Group");Button b=button("👥  "+name+"  · Manage");b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(52));p.topMargin=dp(6);list.addView(b,p);b.setOnClickListener(v->manageGroup(id,name));}});}catch(Exception e){toast("Groups: "+e.getMessage());}});
    }
    private void joinGroup(){
        EditText code=field("Invite code",InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("Join a group").setView(code).setPositiveButton("Join",(d,w)->io.execute(()->{try{JSONObject a=new JSONObject();a.put("p_invite_code",code.getText().toString().trim());SupabaseClient.request("POST","/rest/v1/rpc/accept_group_invite",token(),a,null);runOnUiThread(()->{toast("Joined group.");groups();});}catch(Exception e){toast("Join failed: "+e.getMessage());}})).setNegativeButton("Cancel",null).show();
    }
    private void manageGroup(String id,String name){
        currentGroupId=id;base("Manage · "+name);Button invite=button("Create invite code"),poll=button("Create poll");body.addView(invite,full(dp(44)));LinearLayout.LayoutParams pp=full(dp(44));pp.topMargin=dp(5);body.addView(poll,pp);
        invite.setOnClickListener(v->io.execute(()->{try{JSONObject r=new JSONObject();r.put("conversation_id",id);r.put("created_by",uid());JSONArray made=SupabaseClient.post("/rest/v1/group_invites",token(),r);String code=made.getJSONObject(0).optString("invite_code");runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Invite code").setMessage(code).setPositiveButton("Copy",(d,w)->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("group invite",code));toast("Copied.");}).setNegativeButton("Close",null).show());}catch(Exception e){toast("Invite failed: "+e.getMessage());}}));
        poll.setOnClickListener(v->createPoll(id,name));
        ScrollView sc=new ScrollView(this);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/conversation_members?select=user_id,role,is_muted,is_banned&conversation_id=eq."+enc(id),token());runOnUiThread(()->{list.removeAllViews();for(int n=0;n<rows.length();n++){JSONObject m=rows.optJSONObject(n);if(m==null)continue;String member=m.optString("user_id"),role=m.optString("role","member");Button b=button(member+" · "+role+(m.optBoolean("is_muted")?" · muted":"")+(m.optBoolean("is_banned")?" · banned":""));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(48));p.topMargin=dp(5);list.addView(b,p);if(!member.equals(uid()))b.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Member controls").setItems(new String[]{"Make deputy","Make admin","Make moderator","Make member","Mute / unmute","Ban / unban"},(d,w)->{if(w<4){String[] roles={"deputy","admin","moderator","member"};callRole(id,member,roles[w]);}else callRestriction(id,member,w==4?"mute":"ban",w==4?m.optBoolean("is_muted"):m.optBoolean("is_banned"));}).show();return true;});}});}catch(Exception e){toast("Members: "+e.getMessage());}});
        loadPolls(id,list);
    }
    private void callRole(String group,String user,String role){io.execute(()->{try{JSONObject a=new JSONObject();a.put("p_conversation_id",group);a.put("p_user_id",user);a.put("p_role",role);SupabaseClient.request("POST","/rest/v1/rpc/set_group_member_role",token(),a,null);runOnUiThread(()->{toast("Role updated.");groups();});}catch(Exception e){toast("Role change failed: "+e.getMessage());}});}
    private void callRestriction(String group,String user,String action,boolean wasEnabled){io.execute(()->{try{JSONObject a=new JSONObject();a.put("p_conversation_id",group);a.put("p_user_id",user);a.put("p_action",action);a.put("p_enabled",!wasEnabled);SupabaseClient.request("POST","/rest/v1/rpc/set_group_member_restriction",token(),a,null);runOnUiThread(()->{toast("Member setting updated.");groups();});}catch(Exception e){toast("Moderation failed: "+e.getMessage());}});}
    private void createPoll(String group,String name){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText q=field("Question",InputType.TYPE_CLASS_TEXT),opts=field("Options separated by commas",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);box.addView(q,full(dp(50)));box.addView(opts,full(dp(62)));
        new AlertDialog.Builder(this).setTitle("Create poll").setView(box).setPositiveButton("Publish",(d,w)->io.execute(()->{try{String[] values=opts.getText().toString().split(",");if(q.getText().toString().trim().isEmpty()||values.length<2)throw new Exception("Enter a question and at least two options.");JSONObject r=new JSONObject();r.put("conversation_id",group);r.put("created_by",uid());r.put("question",q.getText().toString().trim());String poll=SupabaseClient.post("/rest/v1/polls",token(),r).getJSONObject(0).getString("id");for(int n=0;n<values.length;n++){if(values[n].trim().isEmpty())continue;JSONObject o=new JSONObject();o.put("poll_id",poll);o.put("option_text",values[n].trim());o.put("position",n);SupabaseClient.post("/rest/v1/poll_options",token(),o);}runOnUiThread(()->{toast("Poll published.");manageGroup(group,name);});}catch(Exception e){toast("Poll failed: "+e.getMessage());}})).setNegativeButton("Cancel",null).show();
    }
    private void loadPolls(String group,LinearLayout host){io.execute(()->{try{JSONArray polls=SupabaseClient.getArray("/rest/v1/polls?select=id,question,poll_options(id,option_text,poll_votes(user_id))&conversation_id=eq."+enc(group)+"&order=created_at.desc",token());runOnUiThread(()->{for(int n=0;n<polls.length();n++){JSONObject p=polls.optJSONObject(n);if(p==null)continue;host.addView(label("Poll: "+p.optString("question"),15,true));JSONArray opts=p.optJSONArray("poll_options");if(opts!=null)for(int j=0;j<opts.length();j++){JSONObject o=opts.optJSONObject(j);if(o==null)continue;String option=o.optString("id");JSONArray votes=o.optJSONArray("poll_votes");Button b=button(o.optString("option_text")+" · "+(votes==null?0:votes.length()));host.addView(b,full(dp(40)));b.setOnClickListener(v->io.execute(()->{try{JSONObject vote=new JSONObject();vote.put("poll_option_id",option);vote.put("user_id",uid());SupabaseClient.upsert("/rest/v1/poll_votes?on_conflict=poll_option_id,user_id",token(),vote);runOnUiThread(()->manageGroup(group,"Group"));}catch(Exception e){toast("Vote failed: "+e.getMessage());}}));}}});}catch(Exception e){toast("Polls: "+e.getMessage());}});}

    private void privacy(){
        base("Privacy");String[] vals={"everyone","contacts","nobody"};String[] labels={"Everyone","My contacts","Nobody"};
        Button last=button("Last seen: choose"),photo=button("Profile photo: choose"),status=button("Status: choose"),save=button("Save privacy");
        body.addView(last,full(dp(46)));body.addView(photo,full(dp(46)));body.addView(status,full(dp(46)));body.addView(save,full(dp(48)));
        String[] selected={"everyone","everyone","contacts"};
        Runnable[] choose=new Runnable[3];
        Button[] controls={last,photo,status};
        for(int i=0;i<3;i++){final int index=i;choose[i]=()->new AlertDialog.Builder(this).setTitle(labelsFor(index)).setItems(index==2?new String[]{"Everyone","My contacts","Nobody","Selected people"}:labels,(d,w)->{selected[index]=index==2?new String[]{"everyone","contacts","nobody","selected"}[w]:vals[w];controls[index].setText(labelsFor(index)+": "+(w==3?"Selected people":labels[w]));}).show();controls[i].setOnClickListener(v->choose[index].run());}
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/user_privacy?select=*&user_id=eq."+enc(uid()),token());if(rows.length()>0){JSONObject r=rows.getJSONObject(0);selected[0]=r.optString("last_seen_visibility","everyone");selected[1]=r.optString("photo_visibility","everyone");selected[2]=r.optString("status_visibility","contacts");}}catch(Exception ignored){}});
        save.setOnClickListener(v->io.execute(()->{try{JSONObject r=new JSONObject();r.put("user_id",uid());r.put("last_seen_visibility",selected[0]);r.put("photo_visibility",selected[1]);r.put("status_visibility",selected[2]);SupabaseClient.upsert("/rest/v1/user_privacy?on_conflict=user_id",token(),r);toast("Privacy settings saved.");}catch(Exception e){toast("Privacy save failed: "+e.getMessage());}}));
    }
    private String labelsFor(int i){return new String[]{"Last seen","Profile photo","Status visibility"}[i];}
    @Override protected void onDestroy(){if(channelPoll!=null)handler.removeCallbacks(channelPoll);if(serverRealtime!=null)serverRealtime.stop();io.shutdown();super.onDestroy();}
}
