try {
  const preparePromptInstallScript = (name, key) => {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", () => preparePromptInstallScript(name, key));
      return;
    }

    // not a text page
    if (document.querySelector('body > pre') == null) return;
    if (document.querySelector("div,p,a") != null) return;

    const meta = document.createElement("meta");
    const style = document.createElement("style");

    style.setAttribute("type", "text/css");
    meta.setAttribute("name", "viewport");
    meta.setAttribute("content", "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no");
    style.textContent = PlaceHolder_cssString;

    document.head.appendChild(meta);
    document.head.appendChild(style);

    const dialog = document.createElement("dialog");
    dialog.id = "confirm";
    const text = document.createElement("p");
    const div = document.createElement("div");
    div.id = "interaction";
    const yes = document.createElement("button");
    const no = document.createElement("button");

    text.textContent = "Install this as UserScript?";
    yes.textContent = "Yes";
    yes.addEventListener("click", (event) => {
      dialog.close();
      if (!event.isTrusted) return;
      Symbol[name].unlock(key).dispatch("installScript", document.URL);
    })
    no.textContent = "No";
    no.addEventListener("click", () => {
      dialog.close();
    })
    div.append(no, yes);
    dialog.append(text, div);
    document.body.append(dialog);
    dialog.showModal();
  }
  preparePromptInstallScript('PlaceHolder_name', PlaceHolder_key);
} catch (e) {
  console.error(e);
}
