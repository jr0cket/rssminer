<!doctype html>
<html>
  <head>
    {{>partials/m_header}}
    <meta name="keywords" content="{{m-keywords}}">
    <meta name="description" content="{{m-description}}">
    {{#mobile-l-css}}<style type="text/css">{{{mobile-l-css}}}</style>{{/mobile-l-css}}
    {{^mobile-l-css}}<link href="/s/css/l.css?{VERSION}" rel="stylesheet" type="text/css" />{{/mobile-l-css}}
  </head>
  <body>
    <div id="header">
      <div class="container">
        <h1>Rssminer</h1>
        <p>{{m-yet-another}}</p>
        <p>
          <a class="btn" href="/login/google">{{m-login-with-google}}</a>
        </p>
        <p class="demo">
          <a href="/demo">{{m-tryout}}</a>
        </p>
      </div>
    </div>
    <div class="seperator">
    </div>
    <div class="container">
      <form action="/" method="post">
      <p>{{m-has-password}}</p>
      {{#login-error}} <p class="msg">{{m-login-error}}</p>{{/login-error}}
        <div><input class="txt" name="email" placeholder="{{m-email}}"/></div>
        <div><input class="txt" type="password" name="password" placeholder="{{m-password}}"/></div>
        <div>
          <label>
            <input type="checkbox" checked name="persistent">
            {{m-persistent}}
          </label>
        </div>
        <input type="hidden" name="return-url" value="/a"/>
        <div class="submit">
          <input type="submit" value="{{m-login}}" />
        </div>
      </form>
    </div>
    </div>
  </body>
</html>
