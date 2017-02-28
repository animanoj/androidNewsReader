package com.example.ani.newsreader;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    ListView listView;
    ProgressBar progressBar;
    Button continueButton;
    int progress;

    ArrayList<String> titles = new ArrayList<>();
    ArrayList<String> content = new ArrayList<>();
    ArrayAdapter arrayAdapter;

    DownloadContent task;
    JSONArray jsonArray;
    int numItem;

    SQLiteDatabase articlesDB;

    public void downloadTask() {
        task = new DownloadContent();
        try {
            task.execute(jsonArray.get(numItem).toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void downloadData(int itemsCompleted) {
        continueButton.setVisibility(View.INVISIBLE);

        if(itemsCompleted <= 0) {
            titles.clear();
            content.clear();
        }

        arrayAdapter.notifyDataSetChanged();

        progress = itemsCompleted * 5;
        progressBar.setProgress(progress);
        progressBar.setVisibility(View.VISIBLE);

        numItem = itemsCompleted;

        DownloadTask task = new DownloadTask();
        try {
            jsonArray = task.execute("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").get();
            if(itemsCompleted <= 0)
                articlesDB.execSQL("DELETE FROM articles");
            downloadTask();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void continueDownload(View view) {
        while(titles.size() > content.size()) {
            articlesDB.execSQL("DELETE FROM articles WHEN id = " + String.valueOf(titles.size()));
            titles.remove(titles.size() - 1);
        }
        while(titles.size() < content.size()) {
            articlesDB.execSQL("DELETE FROM articles WHEN id = " + String.valueOf(content.size()));
            content.remove(content.size() - 1);
        }

        downloadData(titles.size());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = new MenuInflater(this);
        inflater.inflate(R.menu.main_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch(item.getItemId()) {
            case R.id.refresh:
                articlesDB.execSQL("DELETE FROM articles");
                if(task != null)
                    task.cancel(true);
                downloadData(0);
                break;
            default:
                return false;
        }
        return true;
    }

    public class DownloadContent extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            URL url;
            try {
                url = new URL("https://hacker-news.firebaseio.com/v0/item/" + params[0] + ".json?print=pretty");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();
                String articleInfo = "";
                while (data != -1) {
                    articleInfo += (char) data;
                    data = reader.read();
                }

                JSONObject jsonObject = new JSONObject(articleInfo);
                if (!jsonObject.isNull("title") && !jsonObject.isNull("url")) {
                    url = new URL(jsonObject.getString("url"));
                    urlConnection = (HttpURLConnection) url.openConnection();
                    in = urlConnection.getInputStream();
                    reader = new InputStreamReader(in);

                    data = reader.read();
                    String articleContent = "";
                    while (data != -1) {
                        articleContent += (char) data;
                        data = reader.read();
                    }
                    String sql = "INSERT INTO articles (articleId, title, content) VALUES (?, ?, ?)";
                    SQLiteStatement statement = articlesDB.compileStatement(sql);
                    statement.bindString(1, params[0]);
                    statement.bindString(2, jsonObject.getString("title"));
                    statement.bindString(3, articleContent);

                    statement.execute();

                    titles.add(jsonObject.getString("title"));
                    content.add(articleContent);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            arrayAdapter.notifyDataSetChanged();
            progress += 5;
            progressBar.setProgress(progress);

            if(progress == 100)
                progressBar.setVisibility(View.INVISIBLE);

            numItem++;
            if(numItem < 20)
                downloadTask();
        }
    }

    public class DownloadTask extends AsyncTask<String, Void, JSONArray> {

        @Override
        protected JSONArray doInBackground(String... params) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection;

            try {
                url = new URL(params[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();
                while(data != -1) {
                    result += (char) data;
                    data = reader.read();
                }
                return new JSONArray(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = (ListView) findViewById(R.id.listView);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        continueButton = (Button) findViewById(R.id.continueButton);
        continueButton.setVisibility(View.INVISIBLE);
        task = null;

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, DIsplayContentActivity.class);
                intent.putExtra("content", content.get(position));

                startActivity(intent);
            }
        });

        arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(arrayAdapter);

        articlesDB = this.openOrCreateDatabase("Articles", MODE_PRIVATE, null);
        articlesDB.execSQL("CREATE TABLE IF NOT EXISTS articles (id INTEGER PRIMARY KEY, articleId INTEGER, title VARCHAR, content VARCHAR)");

        Cursor c = articlesDB.rawQuery("SELECT * FROM articles", null);
        if(!c.moveToFirst()) {
            downloadData(0);
        } else {
            titles.clear();
            content.clear();

            int contentIndex = c.getColumnIndex("content");
            int titleIndex = c.getColumnIndex("title");

            do {
                titles.add(c.getString(titleIndex));
                content.add(c.getString(contentIndex));
            } while(c.moveToNext());

            arrayAdapter.notifyDataSetChanged();

            if(titles.size() < 20 || content.size() < 20) {
                Log.i("Sizes", titles.size() + " " + content.size());
                continueButton.setVisibility(View.VISIBLE);
            }
        }
        c.close();
    }
}
