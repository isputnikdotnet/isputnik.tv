# Store listings

What goes into the Amazon Appstore and Google Play for the `store` flavor. The plan and its
reasons are in `isputnik.home/docs/tv-store-plan.md` (A = Amazon, G = Google).

- **Upload**: the `store-v<version>` artifact of the tag's Release run — `app-store-release.apk`
  for Amazon, `app-store-release.aab` for Google. Both are signed with the real key.
- **Privacy policy URL**: <https://github.com/isputnikdotnet/isputnik.tv/blob/main/PRIVACY.md>
- **Images** (`images/`): 512×512 icon, 114×114 small icon (Amazon), 1280×720 TV banner (Amazon: Fire TV app icon), Fire TV background 1920×1080 and featured logo 640×260 / background 1920×720 (Amazon),
  1024×500 feature graphic (Google). Made from `Assets/*.svg`.
- **Screenshots** (`screenshots/`): six at 1920×1080, RGB, taken 2026-11-11 from the Fire TV
  running the 0.13.1 store APK, signed in to the demo as the Amazon reviewer (never the
  family's server): Movies, a show's episodes, the player with Skip intro, a film, Photos, a
  photo full screen; and a seventh from 0.14.0, Who's watching? in Kids, with Everyone Locked.
  Retake with `adb exec-out screencap -p > shot.png`; a slideshow needs a few tries to miss
  the crossfade.

## Never

- **Amazon**: tick "keep my own signature" on upload, or Amazon re-signs and no TV installed
  from GitHub can ever update from the store.
- **Google**: Play App Signing → **use an existing key** (upload the current key with PEPK).
  This cannot be changed later; a Google-made key splits the installed base for good.
- Fire tablets and phones: untick them all; the app is TV only (`leanback` required).

## Listing — English

**Title**: iSputnik TV

**Short description** (Google, ≤ 80):
Watch your household's own iSputnik films, shows and photos on the big screen.

**Full description**:

iSputnik TV is the television app for iSputnik, a private media library that a household runs
on its own server at home. It needs an iSputnik server to connect to — the app holds no media
of its own and offers no content.

Connect to your server, then approve the TV from your phone with Link a device: the TV shows a
code, you type it with your password. No password is ever typed on the TV.

• Films and shows from your own libraries, in rows and A–Z
• Continue watching, watched marks, Mark as watched
• Skip intro and credits, subtitles and audio tracks
• Picture quality that suits your connection, with a speed test
• Viewing profiles for the household, a Kids profile and a PIN
• Family photos and videos on the big screen
• Board and arcade games from your server, when it has them

Everything stays between the TV and your server: no accounts with us, no ads, no tracking.

For Android TV, Google TV and Fire TV.

## Listing — Русский

**Название**: iSputnik TV

**Краткое описание** (Google, ≤ 80):
Фильмы, сериалы и фото с вашего домашнего сервера iSputnik на большом экране.

**Полное описание**:

iSputnik TV — приложение для телевизора к iSputnik, личной медиатеке, которую семья держит на
своём сервере дома. Для работы нужен сервер iSputnik: в самом приложении нет ни медиа, ни
контента.

Подключитесь к своему серверу и подтвердите телевизор с телефона через «Привязать устройство»:
телевизор показывает код, вы вводите его со своим паролем. Пароль на телевизоре не вводится.

• Фильмы и сериалы из ваших библиотек — подборками и по алфавиту
• «Продолжить просмотр», отметки о просмотре
• Пропуск заставки и титров, субтитры и звуковые дорожки
• Качество изображения под ваше соединение, проверка скорости
• Профили для всей семьи, детский профиль и PIN-код
• Семейные фото и видео на большом экране
• Настольные и аркадные игры с вашего сервера, если они там есть

Всё остаётся между телевизором и вашим сервером: без учётных записей у нас, без рекламы и
слежки.

Для Android TV, Google TV и Fire TV.

## Questionnaires

- **Category**: Entertainment (Amazon) / Video Players & Editors (Google).
- **Content rating**: no user-generated content shared between users of different
  households, no ads, no purchases. The content is the household's own; answer as a media
  player.
- **Data safety (Google)**: no data collected, no data shared. Data is encrypted in transit
  when the server uses HTTPS (the app warns when it does not).
- **Target audience (Google)**: 18+ / not designed for children. The Kids profile is a
  household filter, not a families-programme claim.
- **Export compliance**: uses only standard HTTPS — exempt.
- **Ads**: none. **In-app purchases**: none. **Price**: free.

## Reviewer notes

Written from the D4 check (`tv-store-plan.md`): what was actually done on the demo, word for
word. The reviewer account and its password are per store and kept off the repo.

> This app needs an iSputnik home server. We run a demo one for review.
>
> 1. Open the app and choose Connect. Type: demo.isputnik.net
> 2. The TV shows a QR code and an address with a code. Scan the QR with any phone, or open
>    the address on a phone or computer. Sign in there with the account below and choose
>    Sign in and link. The TV signs in by itself.
> 3. Who's watching? offers Everyone and Kids. Choose Everyone for everything: films and shows
>    in Movies and Shows, and three photo albums in Photos.
> 4. Kids is a children's profile: choosing it is free, but leaving it (choosing Everyone
>    again, or Unlink this TV in Settings) asks for the household PIN below.
>
> Account: (the store's reviewer member) · Password: (from accounts.txt) · PIN: (household PIN)

The demo as snapshotted 2026-11-12 01:43 UTC: Kids restricted with the PIN set (all video
libraries); Park, Food and Cats on the TV for grown-ups only, so Kids has no Photos. The demo
resets to it nightly. Needs home 4.85.0 or later on the demo for step 2 as written.
