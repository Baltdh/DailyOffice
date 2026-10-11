package com.baltdh.dailyoffice;
import android.content.*;
import android.database.sqlite.*;
import android.database.Cursor;
public final class LedgerDb extends SQLiteOpenHelper {
 public LedgerDb(Context c){super(c,"dailyoffice.db",null,1);}
 @Override public void onCreate(SQLiteDatabase db){
 db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('RECEITA','DESPESA','APORTE','TRANSFERENCIA')), scope TEXT NOT NULL CHECK(scope IN ('PESSOAL','EMPRESA')), description TEXT NOT NULL, cents INTEGER NOT NULL CHECK(cents>0), created_at INTEGER NOT NULL)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
 public void add(String kind,String scope,String description,long cents){
 ContentValues v=new ContentValues();v.put("kind",kind);v.put("scope",scope);v.put("description",description);v.put("cents",cents);v.put("created_at",System.currentTimeMillis());
 getWritableDatabase().insertOrThrow("entries",null,v);
 }
 public long total(String kind,String scope){
 try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(cents),0) FROM entries WHERE kind=? AND scope=?",new String[]{kind,scope})){c.moveToFirst();return c.getLong(0);}
 }
}
