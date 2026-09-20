try {
  if (eruda._isInit) {
    eruda.hide();
    eruda.destroy();
  } else {
    eruda.init();
    eruda.show();
  }
} catch (e) {
  if (typeof define == "function") define.amd = false;
  Symbol.PlaceHolder_name.unlock(PlaceHolder_key).dispatch("loadEruda");
}
