{
  let ChromeXt = Symbol['PlaceHolder_name'].unlock(PlaceHolder_key, false);
  let commands = ChromeXt.commands
    .map((cmd, index) => {
      return {
        index: index,
        scriptName: cmd.id.split(':').at(-1),
        title: cmd.title,
        enabled: cmd.enabled
      }
    }).filter((cmd) => cmd.enabled)
  ChromeXt.dispatch("updateMenuCommands", commands)
}
