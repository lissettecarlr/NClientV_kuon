# NClientV_kuon

NHentai 非官方 Android 客户端，是 [maxwai/NClientV3](https://github.com/maxwai/NClientV3) 的个人定制 fork。
NClientV3 本身又 fork 自 [@Dar9586](https://github.com/Dar9586) 的 [NClientV2](https://github.com/Dar9586/NClientV2)。

适用于 API 26（Android 8）及以上设备。Android 8（SDK 26、27）请使用文件名带 `pre28` 的 APK，该版本的支持会减少。

Releases: <https://github.com/lissettecarlr/NClientV_kuon/releases>

> 应用包名仍是 `com.maxwai.nclientv3`，以便以后合并上游更新。这个 fork 不会向上游提交代码，也不会随上游发布。

## 与上游的区别

自定义代码尽量放在独立的新文件里，改动上游文件的地方用 `[FORK]` 注释标记。

- 隐藏页数少于设定值的漫画（默认 10，设置里可改），被过滤的位置会用后面的内容补满
- 默认隐藏带 `yaoi` / `males only` 标签的漫画（设置里可关；自己搜索这些标签时不隐藏）
- 「已完成」状态与下载联动：下载完成自动标记已完成，标记已完成自动开始下载，下载整页则整页标记已完成
- 标签旁显示中文翻译（仅显示，内部仍是英文，翻译数据来自 [EhTagTranslation](https://github.com/EhTagTranslation/Database)）
- 搜索框输入时联想标签（支持英文和中文），点选后可叠加多个标签一起搜索，也可以切换成排除
- 设置了锁屏验证时，通过链接直接打开页面或从后台回来超过 30 秒也要先验证
- 书籍页去掉分享和评论按钮，其余操作平铺在工具栏上
- 检查更新指向本仓库的 Releases，去掉「下载测试版」选项

## 上游的功能

- Browse main page
- Search by query or tags
- Include or exclude tags
- Blur or hide excluded tags
- Download manga
- Favorite galleries
- Enable PIN to access the app
- Open in browser, bookmark

## 分支与构建

- `main`：发布分支，推送时由 GitHub Actions 编译 debug 包。
- `develop`：开发分支。新功能从 `develop` 切出 `feature/xxx`，完成后合回 `develop`，发版时再从 `develop` 推到 `main`。

本地构建需要 JDK 17 和 Android SDK，并在项目根目录放 `keystore.properties`（仅构建 release 时用到真实值，debug 可以填假值）：

```text
storePassword=dummy
keyPassword=dummy
keyAlias=dummy
storeFile=dummy.jks
```

```bash
./gradlew assemblePost28Debug
```

APK 输出在 `app/build/outputs/apk/post28/debug/`，文件名形如 `NClientV_kuon_4.2.7_debug.apk`。

重新生成标签中文翻译：`python scripts/build_tag_translations.py`（输出到 `app/src/main/assets/tag_zh.json`）。

## 致谢

以下是上游项目 NClientV3 的贡献者：

- [shirokun20](https://github.com/shirokun20) for the initial bug fixes
- [w0x8m](https://github.com/w0x8m) for the new language picker & Chinese Simplified & Traditional translation
- [ananas7](https://github.com/ananas7) for Arabic translation
- [raymi7066](https://github.com/raymi7066) for Chinese Simplified translation
- Худі Таджик (tadzikhudi) for Ukrainian translation
- [PegadaDLancha](https://github.com/PegadaDLancha) for Brazilian Portuguese translation
- Ivan Lost (dovakin1886) for Ukrainian translation
- crorcetn for Japanese translation
- [Inori333](https://github.com/inori-3333) for the initial implementation of the new api
- [Kronos2308](https://github.com/kronos2308) for Spanish translation
- [Locked_Fog](https://github.com/locked-fog) for Chinese Simplified translation
- [zhongshen6](https://github.com/zhongshen6) for bug fixes

以下是原项目 NClientV2 的贡献者：

- [Still34](https://github.com/Still34) for code cleanup & Traditional Chinese translation
- [TacoTheDank](https://github.com/TacoTheDank) for XML and gradle cleanup
- [hmaltr](https://github.com/hmaltr) for Turkish translation and issue moderation
- [ZerOri](https://github.com/ZerOri) and [linsui](https://github.com/linsui) for Chinese translation
- [herrsunchess](https://github.com/herrsunchess) for German translation
- [eme22](https://github.com/herrsunchess) for Spanish translation
- [velosipedistufa](https://github.com/velosipedistufa) for Russian translation
- [bottomtextboy](https://github.com/bottomtextboy) for Arabic translation
- [MaticBabnik](https://github.com/MaticBabnik) for bug fixes
- [DontPayAttention](https://github.com/DontPayAttention) for French translation
- [kuragehimekurara1](https://github.com/kuragehimekurara1) for Japanese translation
- [chayleaf](https://github.com/chayleaf) for Cloudflare bypass
- [Atmosphelen](https://github.com/Atmosphelen) for Ukrainian translation

## Libraries

- OKHttp ([License](https://github.com/square/okhttp/blob/master/LICENSE.txt))
- PersistentCookieJar ([License](https://github.com/franmontiel/PersistentCookieJar/blob/master/LICENSE.txt))
- JSoup ([License](https://github.com/jhy/jsoup/blob/master/LICENSE))
- Glide ([License](https://github.com/bumptech/glide/blob/master/LICENSE))
- AmbilWarna ([License](https://github.com/yukuku/ambilwarna/blob/master/LICENSE))
- AndroidFastScroll ([License](https://github.com/zhanghai/AndroidFastScroll/blob/master/LICENSE))

## License

本项目沿用上游的 Apache License 2.0，完整文本见 [LICENSE](LICENSE)。

```text
Copyright 2024 maxwai

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
