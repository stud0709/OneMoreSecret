# ![App Icon](/app/src/main/res/mipmap-xhdpi/ic_launcher.png) OneMoreSecret
OneMoreSecret is a standalone security layer for your data (e.g. passwords, more to come). It leverages the Android Keystore system, turning your phone into a  [hardware security module](https://source.android.com/docs/security/features/keystore). In other words: with OneMoreSecret, you decrypt your secrets with your phone and your fingerprint. 

### Disclaimer
This is a very early version of the software. Use it at your own risk. We'll do our best to keep the message formats unchanged and guarantee the backward compatibility. 

The app is currently available from Google Play as an internal test. Send me a message if you want to participate, and you will receive an invitation. 

### For the impatient one
TLDR? See it in action! Try our ["Hello, World!" Tutorial](hello_world.md).

⚠️ **WARNING**: The images in this tutorial may potentially trigger seizures for people with photosensitive epilepsy. Viewer discretion is advised.

## What's wrong with password managers?
In the early days, the computers were not password protected. The first password [dates back](https://www.smh.com.au/national/scientist-who-introduced-the-computer-password-20190717-p527zf.html) to 1961. As things got worse, [password policies](https://en.wikipedia.org/wiki/Password_policy) were born, together with the recommendation to have separate passwords for every application. This is how the [password manager](https://en.wikipedia.org/wiki/Password_manager) came into being - as a workaround for the password policy. You *kind of* have different passwords for every service, and still, there is only one password.

Don't get me wrong, [KeePass](https://keepass.info/download.html) and others have been doing a great job. But here are some concerns:

1. A security software with millions of installations is very attractive to hackers.
2. If you know the master password, you have access to the entire database. Not only you get a list of passwords, you also know where to log in - a typical password manager stores everything in one place. If you are extraordinary "smart", you will also store your [One Time Tokens](https://en.wikipedia.org/wiki/One-time_password) configuration in your password manager, thus bypassing the very idea of the [Multi-Factor Authentication](https://en.wikipedia.org/wiki/Multi-factor_authentication).
3. Even if there are some additional security measures to protect the password database (e.g. entering your password using Windows secure screen or protecting the database with the password and a key provider or a secret file), they are often not active in the default configuration of your tool. 
4. If you have gained access to a cloud password storage, you can collect literally millions of password **databases**!

My personal nightmare is a hidden code change in a password manager making it send the data to a third party. And yes, code changes to a cloud software apply for all customers the same minute they are deployed... 💣

## Why OneMoreSecret?
...well, I am probably not the only one wondering if we are really better off with password managers or just storing all our credentials in one place for someone to come and collect them all at once. Maybe not today, but tomorrow...

If there is a vulnerability, there will be also an exploit for it. And it will work for a typical configuration. It is a good idea to be among those 1% with a setup, where the exploit will not work. 

...and I am fed up of typing my master password 40 times a day! 🤬 If you enter your master password on multiple machines and different platforms many times per day - is it still something you call secure?

So here is the wish list I wanted to implement with OneMoreSecret:

### No Master Password
The encryption used in OneMoreSecret is based on keys, not a password phrase. Yes, it's the old good [asymmetric cryptography](https://en.wikipedia.org/wiki/Public-key_cryptography) wrapped into a handy tool. 

### No Context
Every password is stored separately in its own encryption envelope. And every password is sent to the phone for decryption separately and without context. So even if someone steals a password from your phone, he will still have to figure out, what it is good for. 

### Store It Your Way
It's your ~~problem~~ choice how to store your credentials. You could use a text file, Excel, [Google Sheets](https://docs.google.com/spreadsheets), [Simplenote](https://simplenote.com/) or any other software. You could also conitnue using [KeePass](https://keepass.info) (it has a very comfortable user interface after all ❤️) or a password manager of your choice and put your encrypted password into the password field: 

![oms ontop KeePass](readme_images/oms_ontop_keepass.png)

If your database is stolen, the guys will still end up with encrypted passwords. 

⚠️ Whatever you are going to use, think of regular backups, versioning and the offline capability of the software. A pure web application might be unavailable the very moment you need your passwords whereas a cloud storage client can be set up to have also a local copy on your device ([here](https://support.google.com/drive/answer/2375012?co=GENIE.Platform%3DDesktop&oco=1)'s how you set it up for Google Drive). 

### No Private Key Exposure 
The Android Keystore system does not "hand over" the key to the app. Once the key has been imported into the storage, you cannot extract it from the phone any more. 

The only way to restore your private key is the backup document together with the transport password. 

⚠️ DO NOT share this document and password with others as this will grant access to all data encrypted by this private key. 

### Login without a password
...yes, I know, there is [FIDO2](https://fidoalliance.org/). But hey, with OneMoreSecret, your users can share their public key with you - with just one click. Now you can generate a one-time verification code for the user, encrypt is with his key and show it as a QR sequence on your login page ([omsCompanion](https://github.com/stud0709/oms_companion) has already all the logic written in Java). 

Login from a mobile device? No problem, OneMoreSecret will respond to browser links. Just add the encrypted message (`<a href="https://oms-app-intent/oms00_.....">Click here to log in from your phone</a>`). You will find a sample link in our ["Hello, World!" Tutorial](hello_world.md#step-5-mobile-phone-integration).

(As `oms-app-intent` is not a valid domain, this is not working smoothly on all phones right now, sometimes displaying "Page not found" error in the browser.)

## How It Works
This is a brief overview of the functionality. For every screen, you can find a Help menu entry. 

### On Your Smartphone
You have all the toolbox to [encrypt](/password_generator.md) and decrypt passwords on your mobile phone, create and import private keys etc.

The app will also respond to specific links in the web browser (as described [here](#login-without-a-password)). Alternatively, you can select the `oms00_....` piece of text on your phone and share it with OneMoreSecret (OneMoreSecret will register as a recipient of text data).

### On Your Desktop Computer
If you store your passwords on your desktop computer, [omsCompanion](https://github.com/stud0709/oms_companion) will convert your encrypted data into a QR code sequence as soon as you copy it to your clipboard. So on your desktop, a window will pop up:

![QR sequence](readme_images/scan.png)

If we need more than one code, there will be a fast changing sequence of codes in this window, so that it takes maybe a couple of seconds to transfer all the data.

### Decrypting the Data
The App will then request the key from Android Keystore system. Android will ask you to scan your fingerprint, verify it and decrypt the message on behalf of the app ([here](https://developer.android.com/training/articles/keystore) are some technical details). Now you can either make your password visible on the phone or you just tell the app to *TYPE* the password back to your PC. 

## Setting Things Up
You will need a smartphone with Android 12 (API 31) or higher, a fingerprint sensor and a HID Bluetooth Profile (there is an [app](https://play.google.com/store/apps/details?id=com.rdapps.bluetoothhidtester&hl=en&gl=US) to test that). 

⚠️ As the whole thing relies on Android OS and hardware security mechanisms, and every manufacturer has his own hard- and software behind the key store implementation, it's a good idea to choose a smartphone from a renowned manufacturer. We have also seen compatibility issues with older phones which received an Android OS upgrade, but seem to have the older key store under the hood. 

On your Android smartphone, you will need to set up the fingerprint authentification from your system settings. 

If your password database is on your decktop PC, you will also need [omsCompanion](https://github.com/stud0709/oms_companion). omsCompanion will generate QR codes from your encrypted data, making it readable for your phone. You can also use it to encrypt your secrets with the public key. 

Once the password has been decrypted, you can auto-type it back to your PC. For this to work, OneMoreSecret acts as a bluetooth keyboard. See [auto-type](./autotype.md) help page in the app for more details.

## Roadmap and Feedback
For feature requests and bug report, please open a [GitHub Issue](https://github.com/stud0709/OneMoreSecret/issues). 

You can also send me an e-mail from the app *Feedback* menu or use our [Discord channel](https://discord.gg/eBH6U6XqMC).

## Credits
Images:
- [android png from pngtree.com](https://pngtree.com/so/android)

Many thanks to the folks whose projects helped me to find my way through HID, encryption and other challenges:
- [Aleksander-Drewnicki](https://github.com/Aleksander-Drewnicki/BLE_HID_EXAMPLE)
- [Eleccelerator](https://eleccelerator.com/tutorial-about-usb-hid-report-descriptors/)
- [misterpki](https://github.com/misterpki/selfsignedcert)
- [divan](https://github.com/divan/txqr)
- [memorynotfound](https://memorynotfound.com/generate-gif-image-java-delay-infinite-loop-example/)