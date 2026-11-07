package com.chatapp.mobile;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

final class SupabaseStorage {
    private SupabaseStorage() {}
    static String upload(Context context, Uri uri, String conversationId, String token) throws Exception {
        ContentResolver resolver=context.getContentResolver();
        String mime=resolver.getType(uri);
        if(mime==null)mime="application/octet-stream";
        mime=mime.trim().toLowerCase(java.util.Locale.ROOT);
        int mimeParameters=mime.indexOf(';');
        if(mimeParameters>=0)mime=mime.substring(0,mimeParameters).trim();
        if(!isAllowedMimeType(mime)){
            throw new Exception("Unsupported file type: "+mime+". Choose JPEG, PNG, WEBP, GIF, MP4, PDF, or TXT.");
        }
        String attachmentCategory=mime.startsWith("image/")?"image":(mime.startsWith("video/")?"video":"file");
        String ext="bin";
        int slash=mime.indexOf('/');
        if(slash>=0&&slash<mime.length()-1)ext=mime.substring(slash+1).replaceAll("[^A-Za-z0-9]","");
        String path=conversationId+"/"+UUID.randomUUID()+"."+ext;
        URL url=new URL(SupabaseConfig.PROJECT_URL+"/storage/v1/object/chat-media/"+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(20000);c.setReadTimeout(60000);
        c.setRequestProperty("apikey",SupabaseConfig.PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Content-Type",mime);
        c.setRequestProperty("x-upsert","false");
        try(InputStream in=resolver.openInputStream(uri);OutputStream out=c.getOutputStream()){
            if(in==null)throw new Exception("Cannot read selected file");
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
        int code=c.getResponseCode();String response="";
        if(code<200||code>=300){
            if(c.getErrorStream()!=null)response=readText(c.getErrorStream());
            c.disconnect();throw new Exception("Upload failed ("+code+"): "+response);
        }
        c.disconnect();
        return path+"|"+attachmentCategory;
    }
    private static boolean isAllowedMimeType(String mime) {
        return "image/jpeg".equals(mime)
            || "image/png".equals(mime)
            || "image/webp".equals(mime)
            || "image/gif".equals(mime)
            || "video/mp4".equals(mime)
            || "application/pdf".equals(mime)
            || "text/plain".equals(mime);
    }
    private static byte[] readBytes(InputStream in) throws Exception {
        try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;while((n=source.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    private static String readText(InputStream in) throws Exception { return new String(readBytes(in),"UTF-8"); }
    static String signedUrl(String path,String token) throws Exception {
        URL url=new URL(SupabaseConfig.PROJECT_URL+"/storage/v1/object/sign/chat-media/"+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestProperty("apikey",SupabaseConfig.PUBLISHABLE_KEY);
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Content-Type","application/json");
        byte[] body="{\"expiresIn\":3600}".getBytes("UTF-8");
        try(OutputStream out=c.getOutputStream()){out.write(body);}
        int code=c.getResponseCode();
        InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        byte[] bytes=in==null?new byte[0]:readBytes(in);c.disconnect();
        if(code<200||code>=300)throw new Exception("Could not open attachment ("+code+")");
        JSONObject result=new JSONObject(new String(bytes,"UTF-8"));
        String signed=result.optString("signedURL","");
        if(signed.startsWith("http"))return signed;
        if(!signed.startsWith("/"))signed="/"+signed;
        return SupabaseConfig.PROJECT_URL+"/storage/v1"+signed;
    }
}
