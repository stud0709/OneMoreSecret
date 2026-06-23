# ![App Icon](/app/src/main/res/mipmap-xhdpi/ic_launcher.png) OneMoreSecret
OneMoreSecret is a decentralized secrets manager that leverages your smartphone's hardware keystore and biometric authentication to protect your passwords. Instead of relying on a cloud database, your sensitive data (e.g. [passwords](https://github.com/stud0709/OneMoreSecret/wiki/Password-Generator), [TOTP tokens](https://github.com/stud0709/OneMoreSecret/wiki/Time-Based-OTP-Import), [files](https://github.com/stud0709/OneMoreSecret/wiki/File-Encryption), and [Bitcoin private keys](https://github.com/stud0709/OneMoreSecret/wiki/Crypto-Address-Generator)) are encrypted into QR codes or text payloads that can be safely embedded anywhere — even on a public wiki or plain text file.

When you need to use a secret, you simply scan the code or tap the link with your phone, authenticate with your fingerprint to decrypt it locally, and the app acts as a virtual Bluetooth keyboard to instantly ["auto-type"](https://github.com/stud0709/OneMoreSecret/wiki/Auto-Type) the password into your computer. This creates a seamless, air-gapped bridge that keeps your private keys entirely offline, protecting your credentials from keyloggers and cloud breaches while making cross-device authentication completely effortless.

### Disclaimer
This software is provided without any warranty. Use it at your own risk. We'll do our best to keep the message formats unchanged and guarantee the backward compatibility. 


## For the impatient one (TLDR)
[Download](https://github.com/stud0709/OneMoreSecret/releases) the latest release from GitHub or

<a href='https://play.google.com/store/apps/details?id=com.onemoresecret&gl=DE&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img  style="width:200px;height:auto;" alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/>
</a> 
<a href="https://apt.izzysoft.de/fdroid/index/apk/com.onemoresecret/"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" style="width:200px;height:auto;" alt="Get it on IzzyOnDroid"></img></a>

Try our ["Hello, World!" Tutorial](https://github.com/stud0709/OneMoreSecret/wiki/The-Hello-World-Tutorial).

👉 For every screen of the app there is a help page! See the context menu in the upper right corner.

For feature requests and bug report, please open a [GitHub Issue](https://github.com/stud0709/OneMoreSecret/issues). 

You can also send me an e-mail from the app *Feedback* menu or use our [Discord channel](https://discord.gg/eBH6U6XqMC).

See the project's [Wiki](https://github.com/stud0709/OneMoreSecret/wiki) for more details.

## Credits and Legal Attribution
Google Play and the Google Play logo are trademarks of Google LLC.

Images:
- [android png from pngtree.com](https://pngtree.com/so/android)

Many thanks to the folks whose projects helped me to find my way through HID, encryption and other challenges:
- [Aleksander-Drewnicki](https://github.com/Aleksander-Drewnicki/BLE_HID_EXAMPLE)
- [Eleccelerator](https://eleccelerator.com/tutorial-about-usb-hid-report-descriptors/)
- [misterpki](https://github.com/misterpki/selfsignedcert)
- [divan](https://github.com/divan/txqr)
- [memorynotfound](https://memorynotfound.com/generate-gif-image-java-delay-infinite-loop-example/)