# QR Code Scanner
![QR Code Scanner](/readme_images/scan2.png)

This is where you capture the QR code sequence using the camera preview. 

If you are restoring your [private key](./key_import.md) from the backup file, pay attention to the *Remaining Codes* list. In the above example, codes 1 and 3 have not been scanned yet.

## The zxing Switch
Some smartphones (e.g. newer OnePlus devices) seem to be incompatible with the [zebra crossing](https://github.com/zxing/zxing) barcode recognition engine. To work around this issue, the default barcode recognition engine now is [Google ML Kit](https://developers.google.com/ml-kit). You still have the option to switch back to zxing if you have problems with ML Kit on your device. Please let us know if you do so, otherwise zxing engine will be removed from the app in future releases. 

## Toolbar Options
- [Private Key Management](key_management.md)
- [Password Generator](password_generator.md)
- [Encrypt Text](encrypt_text.md)
- [PIN Setup and Phone Lock](pin_setup.md) (🔒)