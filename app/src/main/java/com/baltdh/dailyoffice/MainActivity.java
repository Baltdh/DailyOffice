package com.baltdh.dailyoffice;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.database.sqlite.*;
import android.content.ContentValues;
import android.database.Cursor;
import java.math.BigDecimal;
import java.math.RoundingMode;
public class MainActivity extends Activity {
 SQLiteDatabase db; LinearLayout root; TextView total;
 @Override public void onCreate(Bundle state){super.onCreate(state); db=openOrCreateDatabase("dailyoffice.db",MODE_PRIVATE,null);db.execSQL("CREATE TABLE IF NOT EXISTS entries(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,amount INTEGER NOT NULL,description TEXT NOT NULL)");root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);total=new TextView(this);root.addView(total);EditText description=new EditText(this);description.setHint("Descrição");root.addView(description);EditText amount=new EditText(this);amount.setHint("Valor em R$ (ex: 12,50)");amount.setInputType(8194);root.addView(amount);Spinner kind=new Spinner(this);String[] kinds={"Receita empresarial","Despesa empresarial","Receita pessoal","Despesa pessoal","Aporte pessoal"};kind.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,kinds));root.addView(kind);Button save=new Button(this);save.setText("Salvar lançamento");root.addView(save);save.setOnClickListener(v->{try{String raw=amount.getText().toString().trim().replace(" ",""); if(raw.contains(",") && raw.contains(".")){if(raw.lastIndexOf(",")>raw.lastIndexOf("."))raw=raw.replace(".","").replace(",",".");else raw=raw.replace(",","");}else raw=raw.replace(",",".");java.math.BigDecimal value=new java.math.BigDecimal(raw).setScale(2,java.math.RoundingMode.UNNECESSARY);long cents=value.movePointRight(2).longValueExact();if(cents<=0||description.getText().toString().trim().isEmpty())throw new IllegalArgumentException();ContentValues cv=new ContentValues();cv.put("kind",kinds[kind.getSelectedItemPosition()]);cv.put("amount",cents);cv.put("description",description.getText().toString().trim());db.insertOrThrow("entries",null,cv);amount.setText("");description.setText("");refresh();}catch(Exception e){Toast.makeText(this,"Informe descrição e valor positivo válido",Toast.LENGTH_LONG).show();}});refresh(); }
 void refresh(){Cursor c=db.rawQuery("SELECT kind,SUM(amount) FROM entries GROUP BY kind",null);StringBuilder b=new StringBuilder("Resumo dos lançamentos\n");while(c.moveToNext()){b.append(c.getString(0)).append(": R$ ").append(String.format(java.util.Locale.forLanguageTag("pt-BR"),"%.2f",c.getLong(1)/100.0)).append("\n");}c.close();total.setText(b.toString());}
 @Override protected void onDestroy(){db.close();super.onDestroy();}
}
