document
  .getElementsByTagName("body")[0]
  .classList.add(window._env_.DEFAULT_THEME);
document.getElementById("icon").href = window._env_.DEFAULT_FEVICON;
document.getElementById("title").innerHTML = window._env_.DEFAULT_TITLE;
if (window._env_.DEFAULT_FONT_URL !== "") {
  document.getElementById("fonts-url").href = window._env_.DEFAULT_FONT_URL;
}
