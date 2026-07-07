package com.sft.ingest.link;

/** The Plaid Link page: two buttons, Plaid's stable v2 widget CDN script, fetch-based JS. */
public final class LinkHtml {

    public static final String INDEX = """
        <!doctype html>
        <html>
        <head><meta charset="utf-8"><title>sft link</title></head>
        <body>
          <h1>sft - link an account</h1>
          <button onclick="startLink('bank')">Connect a bank or card</button>
          <button onclick="startLink('brokerage')">Connect a brokerage</button>
          <ul id="log"></ul>
          <script src="https://cdn.plaid.com/link/v2/stable/link-initialize.js"></script>
          <script>
            function log(msg, isError) {
              var li = document.createElement('li');
              li.textContent = msg;
              if (isError) li.style.color = 'red';
              document.getElementById('log').appendChild(li);
            }
            function startLink(flow) {
              fetch('/create_link_token?flow=' + flow, { method: 'POST' })
                .then(function(r) { return r.json(); })
                .then(function(data) {
                  if (data.error) { log('error: ' + data.error, true); return; }
                  var handler = Plaid.create({
                    token: data.link_token,
                    onSuccess: function(public_token, metadata) {
                      var products = (metadata.products || []).join(',');
                      var institution_id = metadata.institution ? metadata.institution.institution_id : null;
                      var institution_name = metadata.institution ? metadata.institution.name : null;
                      fetch('/exchange', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                          public_token: public_token,
                          institution_id: institution_id,
                          institution_name: institution_name,
                          products: products,
                          flow: flow
                        })
                      })
                        .then(function(r) { return r.json(); })
                        .then(function(result) {
                          if (result.error) { log('error: ' + result.error, true); return; }
                          log(result.institution_name + ' - ' + result.account_count + ' account(s) [' + result.products + ']');
                        });
                    },
                    onExit: function(err) {
                      if (err) console.error(err);
                    }
                  });
                  handler.open();
                });
            }
          </script>
          <p>Ctrl-C in the terminal when done.</p>
        </body>
        </html>
        """;

    private LinkHtml() {}
}
