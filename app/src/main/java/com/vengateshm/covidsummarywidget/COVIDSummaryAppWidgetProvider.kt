package com.vengateshm.covidsummarywidget

import android.app.*
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.vengateshm.covidsummarywidget.models.SummaryResponse
import com.vengateshm.covidsummarywidget.network.COVIDApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class COVIDSummaryAppWidgetProvider : AppWidgetProvider() {
    private val TAG = "COVIDSummaryAppWidgetProvider"
    private val REFRESH_BTN_CLICK_ACTION = "refresh_button_click_action"

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate")

        //startUpdateService(context);

        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->
            // Create an Intent to launch ExampleActivity
            val pendingIntent: PendingIntent = Intent(context, MainActivity::class.java)
                .let { intent ->
                    PendingIntent.getActivity(context, 0, intent, 0)
                }
            // Get the layout for the App Widget and attach an on-click listener
            // to the button
            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.covid_summary_layout
            ).apply {
                //setOnClickPendingIntent(R.id.widgetRootLayout, pendingIntent)
                setOnClickPendingIntent(
                    R.id.ivRefresh,
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, COVIDSummaryAppWidgetProvider::class.java).apply {
                            action = REFRESH_BTN_CLICK_ACTION
                        },
                        0
                    )
                )
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun startUpdateService(context: Context) {
        // To prevent any ANR timeouts, we perform the update in a service
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            context.startService(Intent(context, COVIDSummaryUpdateService::class.java))
        else
            context.startForegroundService(Intent(context, COVIDSummaryUpdateService::class.java))
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        Log.d(TAG, "onDisabled")
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive")
        if (REFRESH_BTN_CLICK_ACTION == intent?.action) {
            context?.let { startUpdateService(it) }
        }
    }

    companion object {
        class COVIDSummaryUpdateService : Service() {
            private val TAG = "COVIDSummaryUpdateService"
            private val FOREGROUND_SERVICE_ID = 111

            override fun onBind(intent: Intent?): IBinder? {
                return null
            }

            override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
                startNotificationForForeground()
                val views = RemoteViews(
                    this@COVIDSummaryUpdateService.packageName,
                    R.layout.covid_summary_layout
                )
                views.setViewVisibility(R.id.ivRefresh, View.GONE)
                views.setViewVisibility(R.id.progressBar, View.VISIBLE)
                val componentName = ComponentName(
                    this@COVIDSummaryUpdateService.applicationContext,
                    COVIDSummaryAppWidgetProvider::class.java
                )
                val manager =
                    AppWidgetManager.getInstance(this@COVIDSummaryUpdateService.applicationContext)
                manager.updateAppWidget(componentName, views)

                COVIDApiService.getCOVIDApi().getSummary()
                    .enqueue(object : Callback<SummaryResponse> {
                        override fun onResponse(
                            call: Call<SummaryResponse>,
                            response: Response<SummaryResponse>
                        ) {
                            if (response.isSuccessful && response.code() == 200) {
                                updateWidgetWithResponse(response.body())
                            } else {
                                hideProgressView()
                            }
                            stopService()
                        }

                        override fun onFailure(call: Call<SummaryResponse>, t: Throwable) {
                            t.message?.let { Log.d(TAG, it) }
                            hideProgressView()
                            stopService()
                        }
                    })
                return START_STICKY
            }

            private fun stopService() {
                stopForeground(true)
                stopSelf()
            }

            private fun updateWidgetWithResponse(summaryResponse: SummaryResponse?) {
                try {
                    summaryResponse?.let {
                        val views = RemoteViews(
                            this@COVIDSummaryUpdateService.packageName,
                            R.layout.covid_summary_layout
                        )
                        views.setTextViewText(
                            R.id.tvNewConfirmedCases,
                            "Confirmed Cases - ${summaryResponse.global.newConfirmed}"
                        )
                        views.setTextViewText(
                            R.id.tvNewDeaths,
                            "Deaths - ${summaryResponse.global.newDeaths}"
                        )
                        views.setTextViewText(
                            R.id.tvNewRecovered,
                            "Recovered - ${summaryResponse.global.newRecovered}"
                        )
                        views.setTextViewText(
                            R.id.tvTotalConfirmedCases,
                            "Confirmed Cases - ${summaryResponse.global.totalConfirmed}"
                        )
                        views.setTextViewText(
                            R.id.tvTotalDeaths,
                            "Deaths - ${summaryResponse.global.totalDeaths}"
                        )
                        views.setTextViewText(
                            R.id.tvTotalRecovered,
                            "Recovered - ${summaryResponse.global.totalRecovered}"
                        )
                        views.setViewVisibility(R.id.ivRefresh, View.VISIBLE)
                        views.setViewVisibility(R.id.progressBar, View.GONE)
                        val componentName = ComponentName(
                            this@COVIDSummaryUpdateService.applicationContext,
                            COVIDSummaryAppWidgetProvider::class.java
                        )
                        val manager =
                            AppWidgetManager.getInstance(this@COVIDSummaryUpdateService.applicationContext)
                        manager.updateAppWidget(componentName, views)
                    }
                } catch (e: Exception) {
                    e.message?.let { Log.d(TAG, it) }
                    hideProgressView()
                    stopService()
                }
            }

            private fun hideProgressView() {
                val views = RemoteViews(
                    this@COVIDSummaryUpdateService.packageName,
                    R.layout.covid_summary_layout
                )
                views.setViewVisibility(R.id.ivRefresh, View.VISIBLE)
                views.setViewVisibility(R.id.progressBar, View.GONE)
                val componentName = ComponentName(
                    this@COVIDSummaryUpdateService.applicationContext,
                    COVIDSummaryAppWidgetProvider::class.java
                )
                val manager =
                    AppWidgetManager.getInstance(this@COVIDSummaryUpdateService.applicationContext)
                manager.updateAppWidget(componentName, views)
            }

            private fun startNotificationForForeground() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForeground(
                        FOREGROUND_SERVICE_ID,
                        createNotification(
                            "COVID19Service", "COVID19 Summary Channel",
                            null,
                            getString(R.string.foreground_not_text)
                        )
                    )
                }
            }

            @RequiresApi(Build.VERSION_CODES.O)
            private fun createNotification(
                channelId: String,
                channelName: String,
                contentTitle: String?,
                contentText: String,
                pendingIntent: PendingIntent? = null
            ): Notification {
                val notificationChannel =
                    NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
                notificationChannel.description = channelId
                notificationChannel.setSound(null, null)
                notificationChannel.lightColor = Color.BLUE
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(notificationChannel)

                return Notification.Builder(this, channelId).let { builder ->
                    contentTitle?.let {
                        builder.setContentTitle(contentTitle)
                    }
                    builder.setContentText(contentText)
                    builder.setSmallIcon(R.drawable.ic_covid_19_33)
                    pendingIntent?.let { builder.setContentIntent(it) }
                    builder.build()
                }
            }
        }
    }
}