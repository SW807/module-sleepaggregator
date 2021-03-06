package dk.aau.cs.psylog.analysis.sleepaggregator;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import dk.aau.cs.psylog.module_lib.DBAccessContract;
import dk.aau.cs.psylog.module_lib.IScheduledTask;

/**
 * Created by Praetorian on 23-04-2015.
 */
public class Aggregator implements IScheduledTask{
    Uri stateuri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepaggregator_state");
    Uri resulturi = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepaggregator_result");
    Uri positionuri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepaggregator_position");
    ContentResolver contentResolver;

    SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    public Aggregator(Context context)
    {
        contentResolver = context.getContentResolver();
    }

    Date startDate = null, endDate = null;
    float oldProb = 0.0f;
    boolean inPeriod = false;
    public void Aggregate()
    {
        //Anskaf cursor
        Cursor data = getData();
        if(data == null)
            return;

        initState();
        int toReportPos;
        do {
            toReportPos = data.getInt(data.getColumnIndex("_id"));
            float prob = data.getFloat(data.getColumnIndex("prob"));
            String time = data.getString(data.getColumnIndex("time"));
            //start of period
            if(prob > 0.0f && !inPeriod)
            {
                startDate = convertTimeString(time);
                oldProb = prob;
                inPeriod = true;
            }
            //still in period
            else if(prob > oldProb && inPeriod)
            {
                oldProb = prob;
                endDate = convertTimeString(time);
            }
            //end of period
            else if(prob < oldProb && inPeriod)
            {
                if(oldProb > 0.1f) {
                    if((endDate.getTime()-startDate.getTime()) > 1000*30*60)
                        reportRow(startDate, endDate, oldProb);
                }
                //might be new period
                if(prob > 0.0f)
                {
                    inPeriod = true;
                    oldProb = prob;
                    startDate = convertTimeString(time);
                }
                else
                {
                    inPeriod = false;
                    oldProb = prob;
                }
            }
        }while(data.moveToNext());
        data.close();

        insertState(inPeriod, endDate, startDate, oldProb);
        updatePos(toReportPos);
    }
    private void updatePos(int pos)
    {
        ContentValues values = new ContentValues();
        values.put("pos", pos);
        Cursor cursor = contentResolver.query(positionuri, new String[]{"pos"}, null, null, null);
        if (cursor.getCount() > 0) {
            contentResolver.update(positionuri, values, "1=1", null);
        } else {
            contentResolver.insert(positionuri, values);
        }
        cursor.close();
    }

    private void initState() {
        Cursor cursor = contentResolver.query(stateuri, new String[]{"inPeriod", "newTime", "oldTime", "oldProb"}, null, null, null);
        if(cursor.getCount() > 0)
        {
            cursor.moveToFirst();
            inPeriod = cursor.getInt(cursor.getColumnIndex("inPeriod")) > 0 ? true : false;
            startDate = convertTimeString(cursor.getString(cursor.getColumnIndex("oldTime")));
            endDate = convertTimeString(cursor.getString(cursor.getColumnIndex("newTime")));
            oldProb = cursor.getFloat(cursor.getColumnIndex("oldProb"));
        }
        else
        {
            startDate = null;
            endDate = null;
            oldProb = 0.0f;
            inPeriod = false;
        }
        cursor.close();
    }

    private void insertState(boolean inPeriod, Date oldTime, Date newTime, float oldProb)
    {

        ContentValues values = new ContentValues();
        values.put("inPeriod", inPeriod ? 1 : 0);
        values.put("newTime", sdf.format(newTime));
        values.put("oldTime", sdf.format(oldTime));
        values.put("oldProb", oldProb);
        Cursor cursor = contentResolver.query(stateuri, new String[]{"inPeriod", "newTime", "oldTime", "oldProb"}, null, null, null);
        if (cursor.getCount() > 0) {
            contentResolver.update(stateuri, values, "1=1", null);
        } else {
            contentResolver.insert(stateuri, values);
        }
        cursor.close();
    }
    private void reportRow(Date startDate, Date endDate, float prob)
    {
        ContentValues values = new ContentValues();
        values.put("startdate",sdf.format(startDate));
        values.put("enddate" , sdf.format(endDate));
        values.put("prob", prob);
        contentResolver.insert(resulturi, values);
    }
    private Cursor getData()
    {
        Uri uri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepstationary_sleepcalc");
        //_id prob time
        Cursor cursor = contentResolver.query(uri, new String[]{"_id", "prob", "time"}, "_id > " + getLastPos(), null, "_id");
        if(cursor.moveToFirst())
            return cursor;
        return null;
    }

    private int getLastPos()
    {

        Cursor cursor = contentResolver.query(positionuri, new String[]{"pos"}, null, null,null);
        if(cursor.getCount() > 0)
        {
            cursor.moveToFirst();
            int id = cursor.getInt(cursor.getColumnIndex("pos"));
            cursor.close();
            return id;
        }
        else
        {
            cursor.close();
            return -1;
        }
    }

    private Date convertTimeString(String s){
        Date convertedTime = new Date();
        try {
            convertedTime = sdf.parse(s);
        }catch (ParseException e){
            e.printStackTrace();
        }
        return convertedTime;
    }

    @Override
    public void doTask() {
        Log.i("SleepAggregator", "blev kaldt");
        Aggregate();
        Log.i("SleepAggregator", "blev færdig");
    }

    @Override
    public void setParameters(Intent i) {

    }
}
