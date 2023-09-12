# PIN Setup
![PIN Setup Form](/readme_images/pin_setup.png)

You can protect the app with a PIN. This does not really make sense at a first glance, since the key material is stored in the phone hardware and protected by the finger print. 

But what if someone is forcing you into decoding your data? Well, here's where the PIN system comes in handy. 

- you can enable PIN protection, it will just ask for a PIN when *OneMoreSecret* starts
- additionally, you can ask for the PIN every now and then
- *OneMoreSecret* can also delete all your keys from the key storage after X failed attempts

## Panic Mode
So far so good, but what is the Panic PIN? It's a second PIN that will immediately delete all your keys from the device. 

⚠️ WARNING: 
- the keys will be deleted without further notice
- after the keys have been deleted, Panic PIN will act exactly as the normal PIN saying "PIN accepted"

You can also "lock" the app at any time by pressing 🔒 on the [QR Scanner screen](/qr_scanner.md). If you now try to decode some data, you will have to enter your PIN first.

Once the PIN has been set up, you will see the following PIN pad as required:

![PIN pad](/readme_images/pin_pad.png)

⚠️ The digits will be shuffled every time the pad is displayed.