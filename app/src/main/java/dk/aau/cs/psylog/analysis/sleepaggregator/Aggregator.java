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
                if(prob > 0.1f)
                    reportRow(startDate, endDate, oldProb);
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
        deleteState();
        if(oldProb > 0.0f)
            insertState(inPeriod, endDate, startDate, oldProb);
        updatePos(toReportPos);
    }
    private void updatePos(int pos)
    {
        contentResolver.delete(positionuri, "1=1", null);
        ContentValues values = new ContentValues();
        values.put("pos", pos);
        contentResolver.insert(positionuri, values);
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
    }

    private void insertState(boolean inPeriod, Date oldTime, Date newTime, float oldProb)
    {
        SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        ContentValues values = new ContentValues();
        values.put("inPeriod", inPeriod ? 1 : 0);
        values.put("newTime", sdf.format(newTime));
        values.put("oldTime", sdf.format(oldTime));
        values.put("oldProb", oldProb);
        contentResolver.insert(stateuri, values);
    }
    private void deleteState()
    {
        contentResolver.delete(stateuri, "1=1", null);
    }
    private void reportRow(Date startDate, Date endDate, float prob)
    {
        ContentValues values = new ContentValues();
        SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        values.put("startdate",sdf.format(startDate));
        values.put("enddate" , sdf.format(endDate));
        values.put("prob", prob);
        contentResolver.insert(resulturi, values);
    }
    private Cursor getData()
    {
        Uri uri = Uri.parse(DBAccessContract.DBACCESS_CONTENTPROVIDER + "sleepstationary_sleepcalc");
        int pos = getLastPos();
        //_id prob time
        Cursor cursor = contentResolver.query(uri, new String[]{"_id", "prob", "time"}, null, null, "_id");

        if(pos == -1)
        {
            if(cursor.moveToFirst())
            {
                return cursor;
            }
        }
        else if(cursor.moveToPosition(getLastPos()))
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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date convertedTime = new Date();
        try {
            convertedTime = dateFormat.parse(s);
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
