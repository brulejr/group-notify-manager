/* The console's behaviour: theme, local times, dialogs, notices, the CSRF header, and the
   notification editor (Markdown or WYSIWYG body, delivery rows). Everything else is htmx
   attributes in the pages. */
(function () {
  'use strict';

  // ---- theme ----

  document.addEventListener('click', function (event) {
    if (!event.target.closest('[data-theme-toggle]')) return;
    var next = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', next);
    try { localStorage.setItem('notify-theme', next); } catch (e) { /* private window */ }
  });

  // ---- CSRF: every htmx request carries the token from the page's head ----

  function csrf() {
    var header = document.querySelector('meta[name="csrf-header"]');
    var token = document.querySelector('meta[name="csrf-token"]');
    return header && token ? { header: header.content, token: token.content } : null;
  }

  document.addEventListener('htmx:configRequest', function (event) {
    var c = csrf();
    if (c) event.detail.headers[c.header] = c.token;
  });

  // A boosted page change swaps only the body, so take the new page's token into the head:
  // it is the one that matches the cookie now.
  document.addEventListener('htmx:beforeSwap', function (event) {
    if (!event.detail.boosted || !event.detail.serverResponse) return;
    var match = /<meta name="csrf-token" content="([^"]*)"/.exec(event.detail.serverResponse);
    var token = document.querySelector('meta[name="csrf-token"]');
    if (match && token) token.content = match[1];
  });

  // ---- times, shown in the browser's own zone ----

  var relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

  function relative(date) {
    var seconds = (date.getTime() - Date.now()) / 1000;
    var abs = Math.abs(seconds);
    if (abs < 45) return relativeFormat.format(Math.round(seconds), 'second');
    if (abs < 2700) return relativeFormat.format(Math.round(seconds / 60), 'minute');
    if (abs < 64800) return relativeFormat.format(Math.round(seconds / 3600), 'hour');
    return relativeFormat.format(Math.round(seconds / 86400), 'day');
  }

  function formatTimes(root) {
    root.querySelectorAll('time[datetime]').forEach(function (el) {
      var date = new Date(el.getAttribute('datetime'));
      if (isNaN(date.getTime())) return;
      el.title = date.toLocaleString();
      if (el.dataset.format === 'relative') {
        el.textContent = relative(date);
      } else {
        el.textContent = date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
      }
    });
  }

  setInterval(function () {
    document.querySelectorAll('time[data-format="relative"]').forEach(function (el) {
      var date = new Date(el.getAttribute('datetime'));
      if (!isNaN(date.getTime())) el.textContent = relative(date);
    });
  }, 15000);

  // ---- notices ----

  function showToast(el) {
    if (el.dataset.shown) return;
    el.dataset.shown = 'true';
    el.addEventListener('hidden.bs.toast', function () { el.remove(); });
    var long = el.classList.contains('text-bg-danger') || el.classList.contains('text-bg-warning');
    bootstrap.Toast.getOrCreateInstance(el, { delay: long ? 9000 : 4000 }).show();
  }

  window.notifyToast = function (message, level) {
    var container = document.getElementById('toasts');
    if (!container) return;
    var toast = document.createElement('div');
    toast.className = 'toast align-items-center border-0 text-bg-' + (level || 'danger');
    toast.setAttribute('role', 'status');
    var row = document.createElement('div');
    row.className = 'd-flex';
    var body = document.createElement('div');
    body.className = 'toast-body';
    body.textContent = message;
    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-close btn-close-white me-2 m-auto';
    close.setAttribute('data-bs-dismiss', 'toast');
    close.setAttribute('aria-label', 'Close');
    row.append(body, close);
    toast.append(row);
    container.append(toast);
    showToast(toast);
  };

  new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
      mutation.addedNodes.forEach(function (node) {
        if (node.nodeType !== 1) return;
        if (node.classList.contains('toast')) showToast(node);
        if (node.querySelectorAll) node.querySelectorAll('.toast').forEach(showToast);
      });
    });
  }).observe(document.documentElement, { childList: true, subtree: true });

  document.addEventListener('htmx:responseError', function (event) {
    var xhr = event.detail.xhr;
    var stale = xhr.status === 403 && xhr.getResponseHeader('X-Notify-Denied') === 'csrf';
    var reason = stale ? 'This page is out of date. Reload it and try again.'
      : xhr.status === 403 ? 'You may not do that.' : xhr.status === 404 ? 'It no longer exists, or is not yours to see.'
      : 'The server could not do that (' + xhr.status + ').';
    window.notifyToast(reason);
  });
  document.addEventListener('htmx:sendError', function () {
    window.notifyToast('The application is not answering. Is it running?');
  });

  // ---- dialogs ----

  // A dialog left open would otherwise survive a boosted page change behind the new page.
  document.addEventListener('htmx:beforeSwap', function (event) {
    if (event.detail.boosted) {
      document.querySelectorAll('.modal.show').forEach(function (m) { bootstrap.Modal.getOrCreateInstance(m).hide(); });
      document.querySelectorAll('.modal-backdrop').forEach(function (b) { b.remove(); });
      document.body.classList.remove('modal-open');
      document.body.style.removeProperty('overflow');
      document.body.style.removeProperty('padding-right');
    }
  });

  // hx-confirm asks in a dialog rather than the browser's own prompt.
  document.addEventListener('htmx:confirm', function (event) {
    if (!event.detail.question) return;
    event.preventDefault();
    var dialog = document.getElementById('confirm');
    dialog.querySelector('.confirm-question').textContent = event.detail.question;
    var ok = dialog.querySelector('[data-confirm-ok]');
    ok.textContent = event.detail.elt.getAttribute('data-confirm-label') || 'Confirm';
    var modal = bootstrap.Modal.getOrCreateInstance(dialog);
    var confirmed = function () {
      modal.hide();
      event.detail.issueRequest(true);
    };
    ok.addEventListener('click', confirmed, { once: true });
    dialog.addEventListener('hidden.bs.modal', function () { ok.removeEventListener('click', confirmed); }, { once: true });
    modal.show();
  });

  // ---- the body editors ----
  //
  // The textarea named "body" is always what is submitted. EasyMDE writes to it as it
  // changes (forceSync); Quill is given a host beside it and copied into it on change.

  var editors = new WeakMap();

  var markdownToolbar = [
    { name: 'bold', action: function (e) { EasyMDE.toggleBold(e); }, className: 'bi bi-type-bold', title: 'Bold' },
    { name: 'italic', action: function (e) { EasyMDE.toggleItalic(e); }, className: 'bi bi-type-italic', title: 'Italic' },
    { name: 'strikethrough', action: function (e) { EasyMDE.toggleStrikethrough(e); }, className: 'bi bi-type-strikethrough', title: 'Strikethrough' },
    '|',
    { name: 'heading', action: function (e) { EasyMDE.toggleHeadingSmaller(e); }, className: 'bi bi-type-h2', title: 'Heading' },
    { name: 'quote', action: function (e) { EasyMDE.toggleBlockquote(e); }, className: 'bi bi-quote', title: 'Quote' },
    { name: 'unordered-list', action: function (e) { EasyMDE.toggleUnorderedList(e); }, className: 'bi bi-list-ul', title: 'Bulleted list' },
    { name: 'ordered-list', action: function (e) { EasyMDE.toggleOrderedList(e); }, className: 'bi bi-list-ol', title: 'Numbered list' },
    '|',
    { name: 'link', action: function (e) { EasyMDE.drawLink(e); }, className: 'bi bi-link-45deg', title: 'Link' },
    { name: 'table', action: function (e) { EasyMDE.drawTable(e); }, className: 'bi bi-table', title: 'Table' },
    { name: 'horizontal-rule', action: function (e) { EasyMDE.drawHorizontalRule(e); }, className: 'bi bi-dash-lg', title: 'Rule' },
    '|',
    { name: 'preview', action: function (e) { EasyMDE.togglePreview(e); }, className: 'bi bi-eye no-disable', title: 'Preview', noDisable: true },
    { name: 'undo', action: function (e) { EasyMDE.undo(e); }, className: 'bi bi-arrow-counterclockwise no-disable', title: 'Undo', noDisable: true },
    { name: 'redo', action: function (e) { EasyMDE.redo(e); }, className: 'bi bi-arrow-clockwise no-disable', title: 'Redo', noDisable: true }
  ];

  var quillToolbar = [
    [{ header: [1, 2, 3, false] }],
    ['bold', 'italic', 'underline', 'strike'],
    [{ list: 'ordered' }, { list: 'bullet' }],
    [{ align: [] }],
    ['blockquote', 'code-block', 'link'],
    ['clean']
  ];

  function initMarkdown(textarea) {
    var mde = new EasyMDE({
      element: textarea,
      // The icons are Bootstrap's, served from the jar; never fetch Font Awesome.
      autoDownloadFontAwesome: false,
      spellChecker: false,
      nativeSpellcheck: true,
      status: false,
      forceSync: true,
      minHeight: '16rem',
      placeholder: 'Write the message in Markdown…',
      toolbar: markdownToolbar
    });
    editors.set(textarea, { destroy: function () { mde.toTextArea(); } });
  }

  function quillHtml(quill) {
    if (quill.getLength() <= 1) return '';
    // Quill 2's semantic HTML gives real <ul> and <ol>, which email clients understand,
    // but writes every space as &nbsp;.
    return quill.getSemanticHTML().replace(/&nbsp;/g, ' ');
  }

  function initQuill(textarea) {
    var host = document.createElement('div');
    host.className = 'quill-host';
    var surface = document.createElement('div');
    host.appendChild(surface);
    textarea.style.display = 'none';
    textarea.after(host);
    var quill = new Quill(surface, { theme: 'snow', modules: { toolbar: quillToolbar }, placeholder: 'Write the message…' });
    if (textarea.value) quill.clipboard.dangerouslyPasteHTML(textarea.value, 'silent');
    // Copied only on a change, so opening and saving an untouched body leaves it as stored.
    quill.on('text-change', function () { textarea.value = quillHtml(quill); });
    editors.set(textarea, {
      destroy: function () {
        host.remove();
        textarea.style.display = '';
      }
    });
  }

  function initEditor(textarea) {
    if (editors.has(textarea)) return;
    if (textarea.dataset.format === 'HTML') initQuill(textarea); else initMarkdown(textarea);
  }

  function destroyEditor(textarea) {
    var editor = editors.get(textarea);
    if (editor) editor.destroy();
    editors.delete(textarea);
  }

  // Switching editors rewrites the body on the server, Markdown to HTML or back.
  document.addEventListener('change', function (event) {
    var radio = event.target.closest('[data-format-switch]');
    if (!radio) return;
    var textarea = radio.form.querySelector('textarea[data-editor]');
    var from = textarea.dataset.format;
    var to = radio.value;
    if (from === to) return;
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    var c = csrf();
    if (c) headers[c.header] = c.token;
    fetch(textarea.dataset.convertUrl, {
      method: 'POST',
      headers: headers,
      body: new URLSearchParams({ body: textarea.value, from: from, to: to })
    }).then(function (response) {
      if (!response.ok) throw new Error(response.status);
      return response.text();
    }).then(function (converted) {
      destroyEditor(textarea);
      textarea.value = converted;
      textarea.dataset.format = to;
      initEditor(textarea);
      if (to === 'MARKDOWN' && from === 'HTML') window.notifyToast('Converted to Markdown: colours and alignment are not kept.', 'info');
    }).catch(function () {
      radio.form.querySelector('[data-format-switch][value="' + from + '"]').checked = true;
      window.notifyToast('Could not switch editors.');
    });
  });

  // ---- delivery rows ----

  /** The next free delivery row number; rows are numbered as they are added and may have gaps. */
  window.notifyNextIndex = function () {
    var form = document.getElementById('notification-form');
    var next = parseInt(form.dataset.nextIndex || '0', 10);
    form.dataset.nextIndex = String(next + 1);
    return next;
  };

  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-remove-delivery]');
    if (button) button.closest('.delivery-card').remove();
  });

  // ---- every piece of content htmx adds, the first page included ----

  htmx.onLoad(function (root) {
    formatTimes(root);
    var areas = root.matches && root.matches('textarea[data-editor]') ? [root] : root.querySelectorAll('textarea[data-editor]');
    areas.forEach(initEditor);
  });
})();
