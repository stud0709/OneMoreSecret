# Auto-Type
![auto-type controls](/readme_images/auto-type.png)

With the auto-type feature, OneMoreSecret turns your smartphone into a keyboard. Depending on the context, you can type your password into a desktop application, share a public key as text etc. 

This tool also provides a typical *COPY* and *SEND TO* tool bar buttons for the same content. 

## Setting Up Bluetooth
You can make your phone visible to your PC with ![Bluetooth](/readme_images/bt.jpg). After that, connect to the smartphone from your PC (it will be registered as an input device). 

For auto-type to work, select *Bluetooth-Target* (e.g. your desktop computer) and the correct *Keyboard Layout*. Then press *TYPE*. 

If the the strokes are generated too fast for the target system (this is mostly the case if you are auto-typing into a Remote Desktop session), it can result in a wrong entry. Please activate *delay* in this case.

## On Keyboard Layouts
Long story short: select the *Keyboard Layout* that matches that of your target PC. Activate CAPS-LOCK on your physical keyboard can also result in a wrong input. 

A keyboard is not aware of its layout, the same is true for the auto-type tool. If you press ";" on the US keyboard, there is a key code behind it: 51. Depending on the keyboard layout of your operation system, your PC will make out of it: 
- ";" for English keyboard layout
- "ö" for German keyboard layout
- "ж" for Russian keyboard layout

...or the other way: if I wanted ";" to be typed from the German layout, I would send SHIFT + key code 54 instead. 

Please open a feature request on [GitHub](https://github.com/stud0709/OneMoreSecret/issues) if you need a keyboard layout for your language. 