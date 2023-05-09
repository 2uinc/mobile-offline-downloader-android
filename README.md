## Offline Mode Library

### Usage
##### Add this library as submodule:

```shell
git submodule add git@github.com:2uinc/mobile-offline-downloader-android.git
```

#### Dependencies
##### In build.gradle file of your main module:

```groovy
dependencies {
    implementation project(path: ':mobile-offline-downloader-android')
}
```

##### In settings.gradle file:

```groovy
include ':mobile-offline-downloader-android'
```

##### In the Application class you need to init Offline Mode library:
```kotlin
val client = OkHttpClient.Builder()
    .readTimeout(60, TimeUnit.SECONDS)
    .connectTimeout(60, TimeUnit.SECONDS)
    .addInterceptor(HeadersInterceptor())
    .build()

val offlineBuilder = Offline.Builder()
    .setClient(client)
    .setBaseUrl(Const.getBaseUrl())
    .setOfflineLoggerInterceptor(OfflineLoggerInterceptor())

Offline.init(this, offlineBuilder) {
    OfflineDownloaderCreator(it)
}
```

##### Or simple:
```kotlin
Offline.init(this) { OfflineDownloaderCreator(it) }
```

##### Implement OfflineDownloaderCreator class:
```kotlin
class OfflineDownloaderCreator(offlineQueueItem: OfflineQueueItem) :
    BaseOfflineDownloaderCreator(offlineQueueItem) {

    override fun prepareOfflineDownloader(unit: (error: Throwable?) -> Unit) {
        super.prepareOfflineDownloader(unit)

        // Prepare all data that need for your downloader here
    }

    override fun createOfflineDownloader(unit: (downloader: BaseOfflineDownloader?, error: Throwable?) -> Unit) {
        // Create your downloader here
    }

    override fun destroy() {
        // Destroy all objects here
    }
}
```