package com.intellrecurso.carriersmartremote;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private final String[] MODES={"COOL","DRY","FAN","AUTO"};
    private int temp=24, modeIndex=0, fanIndex=0;
    private boolean power=false, swing=false;
    private TextView tempView,statusView,modeView,fanView;
    private SharedPreferences prefs;
    private LinearLayout root;
    private final int BLUE=Color.rgb(20,115,230), NAVY=Color.rgb(15,39,71), BG=Color.rgb(244,247,251);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("carrier_remote",MODE_PRIVATE);
        temp=prefs.getInt("temp",24); power=prefs.getBoolean("power",false);
        modeIndex=prefs.getInt("mode",0); fanIndex=prefs.getInt("fan",0); swing=prefs.getBoolean("swing",false);
        buildMain();
    }

    private TextView title(String s,int sp){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(NAVY); t.setGravity(Gravity.CENTER);
        t.setPadding(8,8,8,8); return t;
    }
    private Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(15); b.setPadding(10,8,10,8);
        return b;
    }
    private void buildMain(){
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,32); sv.addView(root);
        TextView h=title("Carrier Smart Remote",26); h.setTypeface(null,1); root.addView(h);
        statusView=title("",14); root.addView(statusView); updateStatus();

        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(24,20,24,20);
        card.setBackgroundColor(Color.WHITE); root.addView(card,new LinearLayout.LayoutParams(-1,-2));

        modeView=title(MODES[modeIndex],16); card.addView(modeView);
        tempView=title(temp+"°C",64); tempView.setTypeface(null,1); card.addView(tempView);
        fanView=title("Fan: "+fanLabel(),14); card.addView(fanView);

        Button p=btn(power?"Power ON":"Power OFF"); p.setTextColor(Color.WHITE); p.setBackgroundColor(power?BLUE:NAVY);
        p.setOnClickListener(v->{power=!power; p.setText(power?"Power ON":"Power OFF"); p.setBackgroundColor(power?BLUE:NAVY); save(); sendState("power");});
        card.addView(p,new LinearLayout.LayoutParams(-1,140));

        LinearLayout tr=new LinearLayout(this); tr.setOrientation(LinearLayout.HORIZONTAL);
        Button minus=btn("−"); Button plus=btn("+"); tr.addView(minus,new LinearLayout.LayoutParams(0,120,1)); tr.addView(plus,new LinearLayout.LayoutParams(0,120,1));
        minus.setOnClickListener(v->{if(temp>16)temp--; refresh();sendState("temp");});
        plus.setOnClickListener(v->{if(temp<30)temp++; refresh();sendState("temp");});
        card.addView(tr);

        GridLayout grid=new GridLayout(this); grid.setColumnCount(2); grid.setPadding(0,18,0,0);
        addGrid(grid,"Mode",v->{modeIndex=(modeIndex+1)%MODES.length;refresh();sendState("mode");});
        addGrid(grid,"Fan Speed",v->{fanIndex=(fanIndex+1)%4;refresh();sendState("fan");});
        addGrid(grid,"Swing",v->{swing=!swing;Toast.makeText(this,"Swing "+(swing?"ON":"OFF"),Toast.LENGTH_SHORT).show();save();sendState("swing");});
        addGrid(grid,"Turbo",v->sendSimple("turbo"));
        addGrid(grid,"Sleep",v->sendSimple("sleep"));
        addGrid(grid,"Eco",v->sendSimple("eco"));
        addGrid(grid,"Timer",v->showTimer());
        addGrid(grid,"Settings",v->showSettings());
        root.addView(grid);
        TextView note=title("Wi‑Fi mode works with an ESP32 IR bridge. Direct phone IR requires your exact Carrier IR pulse profile.",12);
        note.setTextColor(Color.DKGRAY); root.addView(note);
        setContentView(sv);
    }
    private void addGrid(GridLayout g,String s,View.OnClickListener l){
        Button b=btn(s); b.setOnClickListener(l);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=130; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(5,5,5,5);
        g.addView(b,lp);
    }
    private String fanLabel(){ return new String[]{"AUTO","LOW","MED","HIGH"}[fanIndex]; }
    private void refresh(){ tempView.setText(temp+"°C");modeView.setText(MODES[modeIndex]);fanView.setText("Fan: "+fanLabel());save(); }
    private void save(){ prefs.edit().putInt("temp",temp).putBoolean("power",power).putInt("mode",modeIndex).putInt("fan",fanIndex).putBoolean("swing",swing).apply(); }
    private String connection(){ return prefs.getString("connection","wifi"); }
    private void updateStatus(){
        if(statusView==null)return;
        if(connection().equals("ir")){
            ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
            statusView.setText(ir!=null&&ir.hasIrEmitter()?"● Phone IR available":"● Phone IR not detected");
            statusView.setTextColor(ir!=null&&ir.hasIrEmitter()?Color.rgb(24,135,84):Color.rgb(190,60,60));
        }else{
            statusView.setText("● Wi‑Fi bridge: "+prefs.getString("esp32","192.168.4.1")); statusView.setTextColor(Color.rgb(24,135,84));
        }
    }
    private void sendState(String source){
        if(connection().equals("ir")) sendIr(source); else sendWifi(source);
    }
    private void sendSimple(String command){
        if(connection().equals("wifi")){
            String ip=prefs.getString("esp32","192.168.4.1");
            request("http://"+ip+"/command?name="+command);
        }else sendIr(command);
    }
    private void sendWifi(String source){
        String ip=prefs.getString("esp32","192.168.4.1");
        String url="http://"+ip+"/ac?power="+(power?1:0)+"&temp="+temp+"&mode="+MODES[modeIndex].toLowerCase(Locale.US)+"&fan="+fanLabel().toLowerCase(Locale.US)+"&swing="+(swing?1:0)+"&source="+source;
        request(url);
    }
    private void request(String u){
        statusView.setText("Sending…");
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(2500); c.setReadTimeout(2500); c.setRequestMethod("GET");
                int code=c.getResponseCode(); runOnUiThread(()->{statusView.setText(code>=200&&code<300?"✓ Command sent":"Bridge returned "+code);statusView.setTextColor(code>=200&&code<300?Color.rgb(24,135,84):Color.rgb(190,60,60));});
                c.disconnect();
            }catch(Exception e){runOnUiThread(()->{statusView.setText("Bridge not reachable");statusView.setTextColor(Color.rgb(190,60,60));});}
        }).start();
    }
    private void sendIr(String name){
        ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        if(ir==null||!ir.hasIrEmitter()){Toast.makeText(this,"This phone has no IR blaster. Use Wi‑Fi/ESP32 mode.",Toast.LENGTH_LONG).show();updateStatus();return;}
        String raw=prefs.getString("irPattern","");
        if(raw.trim().isEmpty()){Toast.makeText(this,"Carrier IR profile is not configured yet. Open Settings and paste the raw pulse pattern for your remote.",Toast.LENGTH_LONG).show();return;}
        try{
            String[] parts=raw.trim().split("[,\\s]+"); int[] pattern=new int[parts.length];
            for(int i=0;i<parts.length;i++)pattern[i]=Integer.parseInt(parts[i]);
            int hz=prefs.getInt("irHz",38000); ir.transmit(hz,pattern); Toast.makeText(this,"IR sent: "+name,Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Invalid IR pulse pattern",Toast.LENGTH_LONG).show();}
    }
    private void showTimer(){
        final EditText minutes=new EditText(this); minutes.setHint("Minutes"); minutes.setInputType(2);
        new AlertDialog.Builder(this).setTitle("Timer").setView(minutes).setPositiveButton("Start",(d,w)->{
            int m=0; try{m=Integer.parseInt(minutes.getText().toString());}catch(Exception ignored){}
            if(m>0){new Handler().postDelayed(()->{power=false;save();sendState("timer");Toast.makeText(this,"AC timer finished",Toast.LENGTH_LONG).show();},m*60000L);Toast.makeText(this,"Timer set for "+m+" min",Toast.LENGTH_SHORT).show();}
        }).setNegativeButton("Cancel",null).show();
    }
    private void showSettings(){
        ScrollView sv=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setPadding(28,20,28,20); box.setOrientation(LinearLayout.VERTICAL); sv.addView(box);
        TextView h=title("Connection Settings",22); h.setTypeface(null,1); box.addView(h);
        RadioGroup rg=new RadioGroup(this); RadioButton wifi=new RadioButton(this); wifi.setText("Wi‑Fi / ESP32 bridge"); RadioButton ir=new RadioButton(this); ir.setText("Phone IR blaster"); rg.addView(wifi);rg.addView(ir);
        if(connection().equals("ir"))ir.setChecked(true); else wifi.setChecked(true); box.addView(rg);
        EditText ip=new EditText(this); ip.setHint("ESP32 address, e.g. 192.168.4.1"); ip.setText(prefs.getString("esp32","192.168.4.1")); box.addView(ip);
        EditText hz=new EditText(this); hz.setHint("IR carrier frequency"); hz.setInputType(2); hz.setText(String.valueOf(prefs.getInt("irHz",38000))); box.addView(hz);
        EditText pattern=new EditText(this); pattern.setHint("Raw IR pulse pattern, microseconds, comma-separated"); pattern.setMinLines(5); pattern.setGravity(Gravity.TOP); pattern.setText(prefs.getString("irPattern","")); box.addView(pattern);
        TextView help=title("For direct IR, capture the pulse pattern from your original Carrier remote using an IR receiver, then paste it here. For Wi‑Fi mode, the ESP32 should expose /ac and /command endpoints.",12); box.addView(help);
        Button save=btn("Save Settings"); box.addView(save); Button back=btn("Back to Remote"); box.addView(back);
        save.setOnClickListener(v->{String c=ir.isChecked()?"ir":"wifi";int f=38000;try{f=Integer.parseInt(hz.getText().toString());}catch(Exception ignored){}
            prefs.edit().putString("connection",c).putString("esp32",ip.getText().toString().trim()).putInt("irHz",f).putString("irPattern",pattern.getText().toString().trim()).apply();
            Toast.makeText(this,"Settings saved",Toast.LENGTH_SHORT).show();});
        back.setOnClickListener(v->buildMain()); setContentView(sv);
    }
    @Override public void onBackPressed(){ buildMain(); }
}
