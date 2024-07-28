# Player Pronouns
Let players share their pronouns!

## For players

### Commands
To change your displayed pronouns, you can use the command `/pronouns`.
It will suggest pronouns that are configured by the server admins, along with the default set. By default, you do not have to pick one of the suggestions at all, however server owners may disable setting custom pronouns in case of abuse, although it is not recommended to do so permanently.

## For server owners

> [!NOTE]
> Player Pronouns needs another mod configured to actually display pronouns, otherwise users will be able to set pronouns, but will not see them anywhere.

### Configuration
The mod should work out of the box without any configuration, however if you want player's pronouns to be visible, you probably want to use the placeholder somewhere.

#### Reloading the config
You can reload the config file using the command `/pronouns reload-config`. This requires either OP level 4 or the permission `playerpronouns.reload_config`.

#### Adding custom pronouns (eg. neo-pronouns)
To add custom pronoun sets, you can use the `single` and `pairs` options in the config file. `single` is for singular options such as `any` or `ask` while `pairs` is for pronouns that come in pairs and are used in the form `a/b`, for example `they` and `them`.

#### Setting the default placeholder
You can configure the default text returned by the placeholder when a player does not have pronouns set by changing the `default_placeholder` config value. You can also override the default in particular cases by passing an argument to the placeholder like this: `%playerpronouns:pronouns/ask%` (or `%playerpronouns:raw_pronouns/ask%`) where `ask` is the default text.

#### Displaying pronouns
You can display the pronouns in any [TextPlaceholderAPI](https://github.com/Patbox/TextPlaceholderAPI) compatible mods using the following placeholders:
* `playerpronouns:pronouns`: Returns a player's pronouns with any styling that is configured.
* `playerpronouns:raw_pronouns`: Returns a player's pronouns without any styling even if configured.

##### In chat with Styled Chat
[Styled Chat](https://modrinth.com/mod/styled-chat) allows you to customise the formatting of chat messages.
To configure pronouns to show up like this, you can set the `chat` style to the following:

`<${player} [%playerpronouns:pronouns%]> ${message}`

![](https://cdn.discordapp.com/attachments/859419898962116642/870732808367267881/in-chat.png)

##### On the tab list with Styled Player List
[Styled Player List](https://modrinth.com/mod/styledplayerlist) allows you to customise the look and feel of the tab/player list, as well as customise the formatting used for players in the list.

```json
{
  "_comment": "Ensure that you include all the other default config options",
  "changePlayerName": true,
  "playerNameFormat": "%player:displayname% (%playerpronouns:pronouns%)",
  "updatePlayerNameEveryChatMessage": true
}
```

![](https://cdn.discordapp.com/attachments/859419898962116642/870739744286453820/2021-07-30_19.45.49.png)

### Backing up the database
The mod stores the mapping of players -> pronouns inside the world save file at `world/playerdata/pronouns.dat`. Note that the file is a custom binary format, NOT NBT, and so cannot be edited using normal tools.
