(function () {
    'use strict';
    var connection = document.getElementById('connection');
    var refresh = document.getElementById('refresh');
    var editVersion = { live: 0, epg: 0 };
    var saveVersion = { live: 0, epg: 0 };

    function request(method, path, body, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, path, true);
        xhr.timeout = 10000;
        if (method === 'POST') xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
        xhr.onload = function () {
            var data;
            try { data = JSON.parse(xhr.responseText); }
            catch (e) { callback('电视返回了无法识别的结果，请刷新页面后重试。'); return; }
            if (xhr.status < 200 || xhr.status >= 300 || data.ok === false) {
                callback(data.message || '保存失败，请重试。');
                return;
            }
            callback(null, data);
        };
        xhr.onerror = function () { callback('无法连接电视，请检查手机与电视是否在同一局域网。'); };
        xhr.ontimeout = function () { callback('连接超时，请在电视上确认配置是否已保存，再重试。'); };
        xhr.send(body || null);
    }

    function current(type, value) {
        document.getElementById(type + '-current').textContent = '当前配置：' + (value || '未设置');
    }

    function loadConfig() {
        var versions = { live: editVersion.live, epg: editVersion.epg };
        var saves = { live: saveVersion.live, epg: saveVersion.epg };
        refresh.disabled = true;
        connection.className = 'connection';
        connection.textContent = '正在连接电视…';
        request('GET', '/api/config', null, function (error, data) {
            refresh.disabled = false;
            if (error) {
                connection.className = 'connection error';
                connection.textContent = error;
                return;
            }
            connection.textContent = '已连接电视 · ' + window.location.host;
            ['live', 'epg'].forEach(function (type) {
                var value = data[type + 'Url'] || '';
                if (saveVersion[type] === saves[type]) current(type, value);
                if (editVersion[type] === versions[type]) {
                    document.getElementById(type + '-url').value = value;
                }
            });
        });
    }

    ['live', 'epg'].forEach(function (type) {
        var form = document.getElementById(type + '-form');
        var input = document.getElementById(type + '-url');
        var result = document.getElementById(type + '-result');
        var button = form.querySelector('button');
        input.addEventListener('input', function () { editVersion[type]++; });
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            if (button.disabled) return;
            var url = input.value.trim();
            result.className = 'result visible error';
            if ((type === 'live' && !url) || (url && !/^https?:\/\/[^\s/]+/i.test(url))) {
                result.textContent = '请输入完整的 http:// 或 https:// 地址。';
                input.focus();
                return;
            }
            button.disabled = true;
            var label = button.textContent;
            button.textContent = '正在保存…';
            result.className = 'result visible';
            result.textContent = '正在发送到电视，请稍候…';
            editVersion[type]++;
            request('POST', '/action', 'do=' + type + '&url=' + encodeURIComponent(url), function (error, data) {
                button.disabled = false;
                button.textContent = label;
                result.className = 'result visible' + (error ? ' error' : '');
                result.textContent = error || data.message;
                if (!error) {
                    saveVersion[type]++;
                    current(type, url);
                }
            });
        });
    });
    refresh.addEventListener('click', loadConfig);
    loadConfig();
}());
