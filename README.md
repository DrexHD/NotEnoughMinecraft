# NotEnoughMinecraft
Did you ever want to play Minecraft inside of Minecraft without installing any mods on the client? NO??? Anyways, now you can!

![](https://cdn.modrinth.com/data/sbnEegTp/images/bcef27b3d1402377ed81f971e20723e57a6218bb.png)

## Setup
1. Install [fabric-api](https://modrinth.com/mod/fabric-api) and [polymer](https://modrinth.com/mod/polymer).
2. This mod requires a server resource pack. The easiest way to host this is by enabling [polymers autohost](https://polymer.pb4.eu/latest/user/resource-pack-hosting/#enabling-autohost)
3. Give yourself a computer `/give @s not-enough-minecraft:computer` and place it down.
4. Right-click on a computer to start playing

## Controls
- The input for movement, jumping and sprinting is the same as your default inputs
- Use the scroll wheel or number keys to navigate the hotbar
- You can sneak to stop playing and exit the computer
- You can press your pick block key to toggle a fps counter

*Because this is a serverside-only mod, it is limited to inputs the client sends to it!*

## How it works
When playing, a fake player is created and added to the server. Each tick an image is rendered using a [server-side 
raytracing renderer](https://modrinth.com/mod/camera-obscura) from [tomalbrc](https://modrinth.com/user/tomalbrc).
The rendered image is then displayed to the client using a custom screen item, where each pixel can be colored
using [custom model data](https://minecraft.wiki/w/Data_component_format#custom_model_data) colors.

## Config
The config is located at `config/not-enough-minecraft.json`.

```json5
{
  // Render distance in blocks
  "renderDistance": 64,
  // Flag for experimental entity rendering
  "renderEntities": false,
  // Amount of entities to render when entity rendering is enabled
  "renderEntitiesAmount": 20,
  // Flag wether light levels should be ignored, rendering everything with the highest light level. 
  "fullbright": false,
  // Field of View, functions the same way the vanilla client FOV works, minimum value is 30, maximum 110 (Quake Pro).
  "fov": 70,
  // Biome blend value
  "biomeBlend": 1,
  // How many threads should be render all instances. This can be used to increase or reduce the amount of CPU usage on 
  // the server and has a direct impact on FPS count
  "renderThreadCount": 4
}

```

## Licenses
This mod uses the rendering code from https://github.com/tomalbrc/camera-obscura. All code in the 
`de.tomalbrc.cameraobscura` package is licensed under [LGPL-3.0-only](https://github.com/DrexHD/NotEnoughMinecraft/blob/main/licenses/camera-obscura).
The idea of using a custom screen item to display the rendered image is taken from https://github.com/tomalbrc/BlockBoy-Arcade, 
which is licensed under [MIT](https://github.com/DrexHD/NotEnoughMinecraft/blob/main/licenses/blockboy-arcade)

## Limitations
*This is a quite cursed/hacky weekend project.* 

Rendering on the server like this is quite inefficient and will increase CPU usage significantly, which can cause 
issues on lower end setups.

We don't have any way to compress or optimize the screen data for sending it over the network, so we have to send the 
raw pixels each frame. This can lead to an increased network load when viewing computer screens. 
You can expect 300-400 KiB/s increase per screen with default minecraft network compression.

Fake players will behave like regular players in almost all aspects, such as world interactions, commands, 
player list, player data and more.
Each player gets their own unique fake player companion, which has the same UUID, but with the lowest bit increase by one.