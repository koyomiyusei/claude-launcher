# Claudeランチャー（Android）

claude.ai の特定のチャットやプロジェクトを、ホーム画面のアイコンから直接開くためのアプリ。

Chrome の「ホーム画面に追加」だと claude.ai の PWA 設定（start_url が `/`）に上書きされて
トップ画面に飛んでしまうため、URL をそのまま保持して開く小さなアプリを自前で用意している。

## 使い方

1. アプリを開くと登録済みの一覧が出る
2. 「＋ 追加」で 名前 / URL / アイコン文字 / 色 を入れて保存
3. 「ホーム画面に追加しますか？」で追加 → ホーム画面にアイコンが置かれる
4. 既存の項目はタップで操作メニュー（ホーム画面に追加・開く・編集・削除）、長押しで編集

### 登録を楽にする経路（推奨）

- **共有メニュー**: Chrome でチャットを開いて「共有」→「Claudeランチャー」を選ぶと、
  URL とページタイトルが入った状態で追加画面が開く。タイトル末尾の「 - Claude」は自動で落とす
- **クリップボード検知**: claude.ai の URL をコピーした状態でアプリを開くと
  「これを追加しますか？」と聞いてくる。登録済みの URL と同じなら黙っている

手入力もできる。追加画面の「クリップボードから貼り付け」でも可。

- チャット: `https://claude.ai/chat/<チャットID>`
- プロジェクト: `https://claude.ai/project/<プロジェクトID>`
- プロジェクト一覧: `https://claude.ai/projects`

### 開き先アプリの指定

項目ごとに「開き方」を選べる。claude.ai の URL を開けるアプリを実行時に列挙して
一覧に出すので、Chrome / Claude アプリ / claude.ai の PWA（WebAPK）から選ぶ。
「自動」なら Android の既定に任せる。

PWA を選びたい場合は、先に Chrome で claude.ai を開いてメニューから
「アプリをインストール」しておく。インストールされた PWA は WebAPK という
実体のあるパッケージになり、リンクの受け取り先として指定できるようになる。
インテント経由で開くと manifest の start_url ではなく **指定した URL がそのまま開く**
ので、ホーム画面ショートカットのようにトップへ飛ばされることはない。

## ビルド

Android Studio の SDK を直接叩くだけの構成（Gradle 不使用・ネット接続不要）。

```
build.bat をダブルクリック
```

成功すると同じフォルダに `ClaudeLauncher.apk` ができる。
経過と失敗理由はすべて `build.log` に出る。

必要なもの:
- Android SDK（`%LOCALAPPDATA%\Android\Sdk`）の build-tools と platforms
- JDK（Android Studio 同梱の jbr を自動で探す）

## インストール

APK をスマホに送って開く。提供元不明アプリの許可を一度求められる。
署名はデバッグ用の自己署名なので、Play ストア配布はできない（自分用）。

## 構成

```
AndroidManifest.xml
build.bat                 ビルドスクリプト
res/values/strings.xml
res/mipmap-xxhdpi/ic_launcher.png
src/com/rerise/claudelauncher/
  MainActivity.java       一覧・追加・編集・ピン留め
  OpenActivity.java       ショートカットから URL を開く
  Store.java              SharedPreferences への保存
  IconGen.java            アイコン画像の生成
  Entry.java              1件分のデータ
```
