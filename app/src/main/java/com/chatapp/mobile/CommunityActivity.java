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
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF13BDAF,0xFF315FEA});
        d.setCornerRadius(dp(15));b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x44FFFFFF),d,null));b.setMinHeight(dp(44));return b;
    }
    private EditText field(String hint,int type){
        EditText e=new EditText(this);e.setSingleLine(false);e.setHint(hint);e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF8293AE);e.setTextSize(14);e.setInputType(type);e.setPadding(dp(13),dp(8),dp(13),dp(8));
        GradientDrawable d=new GradientDrawable();d.setColor(0xFF1B2A40);d.setCornerRadius(dp(13));d.setStroke(dp(1),0xFF344A66);e.setBackground(d);return e;
    }
    private LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(-1,h);}
    private void base(String title){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(12));root.setBackgroundColor(0xFF0B1423);setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView heading=label(title,20,true);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button back=button("Back");bar.addView(back, new LinearLayout.LayoutParams(dp(82),dp(42)));back.setOnClickListener(v->home());
        root.addView(bar,full(dp(52)));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void home(){
        base("Infinity Chat · Community");
        addNav("Statuses","Share a photo, video or text that expires after 24 hours",this::statuses);
        addNav("Channels","Discover, follow and publish channel updates",this::channels);
        addNav("Groups","Manage members, roles, invites and polls",this::groups);
        addNav("Privacy","Control last-seen, photo and status visibility",this::privacy);
        addNav("Join a group","Enter an invite code",this::joinGroup);
    }
    private void addNav(String title,String sub,Runnable action){
        Button b=button(title+"  ›");b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=full(dp(54));p.topMargin=dp(9);body.addView(b,p);
        TextView t=label(sub,12,false);t.setTextColor(0xFFAAB8CD);LinearLayout.LayoutParams q=full(dp(34));q.leftMargin=dp(8);body.addView(t,q);b.setOnClickListener(v->action.run());
    }
    @Override protected void onCreate(Bundle b){super.onCreate(b);if(uid().isEmpty()){finish();return;}home();}

    private void statuses(){
        base("Statuses");
        EditText text=field("Write a text status…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);body.addView(text,full(dp(64)));
        Button publish=button("Publish text status"),media=button("Add photo or video");
        LinearLayout.LayoutParams p=full(dp(46));p.topMargin=dp(7);body.addView(publish,p);p=full(dp(46));p.topMargin=dp(6);body.addView(media,p);
        TextView hint=label("Statuses expire automatically after 24 hours.",12,false);hint.setTextColor(0xFFAAB8CD);body.addView(hint,full(dp(34)));
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
        io.execute(()->{try{JSONArray rows=SupabaseClient.getArray("/rest/v1/channel_posts?select=id,body,created_at,channel_post_reactions(emoji,user_id)&channel_id=eq."+enc(id)+"&order=created_at.desc&limit=100",token());runOnUiThread(()->{list.removeAllViews();for(int n=0;n<rows.length();n++){JSONObject r=rows.optJSONObject(n);if(r==null)continue;TextView t=label(r.optString("body",""),15,false);t.setPadding(dp(12),dp(12),dp(12),dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFF1D3049);bg.setCornerRadius(dp(14));t.setBackground(bg);LinearLayout.LayoutParams p=full(-2);p.topMargin=dp(6);list.addView(t,p);}});}catch(Exception e){toast("Posts: "+e.getMessage());}});
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
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
