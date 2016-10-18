# android-LocalMediaServer 简介

`android-LocalMediaServer` 用来在本地搭建一个 MediaServer，用于提供 `Audio/Video` 数据服务。

### 1、用途

一般来说，本地多媒体文件访问可以通过下面的方法来播放：

```java
// ...

String filePath = Environment.getExternalStorageDirectory().getPath() + "/media.mp4";
MediaPlayer mediaPlayer = new MediaPlayer();

try {
    mediaPlayer.setDataSource(this, Uri.fromFile(new File(filePath)));
} catch (IOException e) {
    e.printStackTrace();
    return ;
}

mediaPlayer.prepare();
mediaPlayer.start();

// ...
```

但如果碰上一些`特殊需求`——比如视频文件加密后缓存在本地，然后后续再进行播放。

针对上面的需求，`Android` 系统自带的 MediaPlayer 将不再适用——它不会识别/解密我们通过特定算法加密的视频文件。要播放这种文件，有下面几种方法：

1. 将文件先解密到本地文件夹，然后再播放解密后的文件；
2. 通过本例提供的本地 MediaServer 的方法，边解密，边播放。

### 2、方案分析

上面提供的方法，第一种会泄露文件——解密后用户仍然可以将解密后的文件拷贝出去；第二种（亦即本例）方法安全性稍有提高，但仍然`无法彻底避免`加密后的视频文件被获取到——充其量只是`提高了一点技术壁垒`，增加了大量复制传播的成本而已。

通过 LocalMediaServer，将一个本地文件转换为类似 `http://localserver:port/encode_filepath` 类似的 URL，然后通过 Android 中的 `ServerSocket` 监听 port 端口，按 **`Http Live Streaming`**（利用好 **206** Http 返回码以及 `Content-Range` 字段） 的要求将数据返回即可。在返回数据时，根据数据段先解密相应数据，再返回数据。

这种方案，如果别人要获取你解密的视频，其实也是比较容易的——因为他可以在手机端抓包，获取到你正在播放的 URL，然后他用一个 HTTP 请求，就可以很容易的将你的整个文件 Download 下来。

总体来说，除非你彻底定制一个自己的 MediaPlayer，实现视频文件的解密、播放，否则单纯利用 Android 系统的 `MediaPlayer`，是无法彻底保证视频文件不被复制的。

### 3、使用示例

具体例子详见工程中的 Demo。下面贴出部分代码示例：

```java
String mediaPath = Environment.getExternalStorageDirectory().getPath() + "/media.mp4";
LocalMediaServer mediaServer = new LocalMediaServer();
String url = mediaServer.prepare(mediaPath);
if (TextUtils.isEmpty(url)) {
    String msg = String.format("Cannot resolve Media-Url ! ensure file \"%s\" exists!", mediaPath);
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    return;
}

mediaServer.start();

MediaPlayer mediaPlayer = new MediaPlayer();
// ...

try {
    mediaPlayer.setDataSource(this, Uri.parse(url));
    mediaPlayer.prepareAsync();
} catch (IOException e) {
    e.printStackTrace();
}

// ...
```

自定义解密器：

```java
byte[] encryptKey = ...;
final Encryptor encryptor = new Encryptor() {
    @Override
    public byte[] encrypt(byte[] buffer, byte[] encryptKey) {
		// ...
    }

    @Override
    public byte[] decrypt(byte[] buffer, byte[] encryptKey) {
		// ...
    }

    @Override
    public byte[] encrypt(byte[] buffer, int start, int count, byte[] encryptKey) {
		// ...
    }

    @Override
    public byte[] decrypt(byte[] buffer, int start, int count, byte[] encryptKey) {
		// ...
    }
};

LocalMediaServer mediaServer = new LocalMediaServer() {
	@Override
    protected MediaDataProvider createMediaProvider(String filePath) {
        return new EncryptedMediaProvider(filePath, encryptor, encryptKey);
    }
};

// ...
String url = mediaServer.prepare(mediaPath);
mediaServer.start();
```
