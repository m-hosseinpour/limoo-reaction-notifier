<div dir="rtl">

### راه‌اندازی بات:
1. یک فایل `config.properties` در یک جایی از سیستم ایجاد کرده و آدرس آن را در متغیر محیطی `LIMOO_REACTION_NOTIFIER_CONFIG` قرار دهید. یا به سادگی فقط فایل config.properties موجود در کد را تغییر دهید (به جای ایجاد کانفیگ لوکال)
2. در فایل `config.properties` که ایجاد کرده‌اید (یا فایل کانفیگ موجود در کد)، موارد زیر را تنظیم کنید:
</div>

```properties
bot.limooUrl=https://web.limoo.im/Limonad
bot.username=bot_username
bot.password=bot_password
store.file.path=/opt/limooReactionNotifier/store.data
```

<div dir="rtl">

3. اگر از لینوکس استفاده می‌کنید، ابتدا دستور زیر را اجرا کنید:
</div>

```bash
chmod +x gradlew
```

<div dir="rtl">

4. دستور زیر را برای شروع به کار بات اجرا کنید:
</div>

```bash
./gradlew runBot
```

<div dir="rtl">

همچنین می‌توانید به جای استفاده از تسک gradle بالا برای اجرای بات، با دستور
</div>

```bash
./gradlew jar
```

<div dir="rtl">

یک جر از بات خود بسازید (که در مسیر build/libs ایجاد می‌شود) و سپس با دستور زیر آن را اجرا کنید:
</div>

```bash
java -jar limoo-reaction-notifier.jar
```
***
