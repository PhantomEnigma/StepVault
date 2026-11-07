# WorkManager creates workers by reflection from their class name.
-keep class com.stepvault.app.work.StepSyncWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
