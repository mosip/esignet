var body = document.getElementsByTagName("body")[0];
if (body) {
  body.classList.add(window._env_.DEFAULT_THEME);
}

var icon = document.getElementById("icon");
if (icon) {
  icon.href = window._env_.DEFAULT_FAVICON;
}

var title = document.getElementById("title");
if (title) {
  title.textContent = window._env_.DEFAULT_TITLE;
}

if (window._env_.DEFAULT_FONT_URL !== "") {
  var fontsUrl = document.getElementById("fonts-url");
  if (fontsUrl) {
    fontsUrl.href = window._env_.DEFAULT_FONT_URL;
  }
}
