# MKSC (Make Shortcut)
MKSC is a small CLI utility to create `.desktop` files (NOT symlinks) in the style of Windows `.lnk` shortcut files. The project is pretty useless, I know, but it was only made for fun, so I don't really care. It can create shortcuts to files, folders or other `.desktop` files. Even custom icons can be specified using the `--icon` option, in case the automatically selected one isn't the one you want.

## Usage
It can be used as a command-line tool as is, but if it has any real-world use-cases at all, that would probably be as a component invoked by another system, likely a graphical one. 
Here's how the program works:

**The `--help`/`-h` option** gives you general information about the program as well as the options it has:
```bash
mksc --help
```


**The `--version`/`-v` option** gives you the current version of the tool as well as the link to this repository:
```bash
mksc --version
```


**The actual usage** of the program is as follows:
```bash
mksc <file> [output file] [options]
```

This means you can simply run (for example):
```bash
mksc cat.png
```
...and this will generate a cat.desktop file which is a shortcut to cat.png, clicking on it in a GUI file manager for example will open the cat.png file. If you had wanted the shortcut to be called just "kitten" for example, you could've ran `mksc cat.png kitten`. Now if you wanted that shortcut to have the firefox icon for some reason, you could just add `--icon firefox` to the end. If a file called `kitten` already exists though, you can add `--overwrite` if you're sure you don't need that file. You can also add `--desktop` to create the shortcut directly onto your desktop.

## Installation
You can just download a prebuilt binary from the releases and put it in a location like `/usr/local/bin` if you're on a supported platform (`linux_x64` or `linux_arm64`).
If you're not (or you just want to), you can build it from source. Note that you are still limited by which platforms implement POSIX APIs and which platforms the Kotlin/Native compiler supports (and in actual use cases by which platforms make use of the FreeDesktop `.desktop` file specification). 

**To build from source:**
1. Clone the repository.
```bash
git clone https://github.com/Uippao/mksc.git
```
2. Run the build script. It will create two distributable `.zip` files in /build which you can delete, as well as a binary in the project root for your host. You will have to modify the script or compile manually if your architecture isn't supported.
```bash
cd mksc
chmod +x build.kts
./build.kts
```
3. Move the binary to a location in your path (A recommended one would be `/usr/local/bin`).

## Desktop Integration
In case you want some use of it, there is in fact a way to use this to add some functionality to your graphical desktop with relative ease. Namely, you can add some context menu actions kind of like how you can add shortcuts to desktop on Windows. The process depends on your DE, but as a KDE user, I'll provide instructions for that, and may add more later if I feel like it.
### KDE
1. Make a new file called `mksc.desktop` into `~/.local/share/kio/servicemenus/`
2. Inside the file, paste the following:
```ini
[Desktop Entry]
Type=Service
X-KDE-ServiceTypes=KonqPopupMenu/Plugin
MimeType=inode/directory;application/octet-stream;application/x-executable;application/x-desktop;image/*;video/*;text/*;
Actions=CreateShortcut;CreateDesktopShortcut;
X-KDE-Priority=TopLevel

[Desktop Action CreateShortcut]
Name=Create Shortcut Here
Icon=insert-link
Exec=sh -c 'mksc "%f" && chmod +x "$(basename "%f").desktop"'

[Desktop Action CreateDesktopShortcut]
Name=Add Shortcut to Desktop
Icon=user-desktop
Exec=sh -c 'mksc "%f" --desktop && chmod +x ~/Desktop/"$(basename "%f").desktop"'
```
3. Open a terminal in the folder and make the file executable with `chmod +x mksc.desktop`
