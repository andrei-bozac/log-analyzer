(() => {
    'use strict';

    const MAX_FILE_BYTES = 10 * 1024 * 1024;
    const STATUS_POLL_MS = 10000;
    const SVG_NS = 'http://www.w3.org/2000/svg';

    const TYPE_LABELS = {
        BRUTE_FORCE: 'Brute-force',
        SCANNING: 'Scanare',
        ATTACK_PATTERN: 'Tipar de atac',
        HIGH_VOLUME: 'Volum anormal',
    };
    const SEVERITY_CLASSES = { HIGH: 'high', MEDIUM: 'medium', LOW: 'low' };
    const SERVICE_STATE_TEXT = { up: 'online', down: 'offline', unknown: '—' };

    const $ = (id) => document.getElementById(id);
    const numberFormat = new Intl.NumberFormat('ro-RO');
    const formatNumber = (value) => numberFormat.format(value);



    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function svg(name, attributes) {
        const node = document.createElementNS(SVG_NS, name);
        for (const [key, value] of Object.entries(attributes || {})) {
            node.setAttribute(key, String(value));
        }
        return node;
    }

    function svgText(name, text, attributes) {
        const node = svg(name, attributes);
        node.textContent = text;
        return node;
    }

    function tick(text, x, y, anchor) {
        return svgText('text', text, { x, y, 'text-anchor': anchor, class: 'tick' });
    }



    function formatBytes(bytes) {
        return bytes < 1024 * 1024
            ? `${Math.round(bytes / 1024)} KB`
            : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }

    function formatTime(iso) {
        return new Date(iso).toISOString().slice(0, 19).replace('T', ' ');
    }

    function formatInterval(first, last) {
        const start = formatTime(first);
        const end = formatTime(last);
        if (start === end) return start;
        return start.slice(0, 10) === end.slice(0, 10)
            ? `${start} → ${end.slice(11)}`
            : `${start} → ${end}`;
    }



    function setMessage(text, kind) {
        const message = $('message');
        message.textContent = text;
        message.dataset.kind = kind || '';
    }

    function setBusy(busy) {
        $('analyze-btn').disabled = busy;
        $('sample-btn').disabled = busy;
        $('upload-form').setAttribute('aria-busy', String(busy));
    }

    function validateFile(file) {
        if (!file) return 'Alege un fișier de log.';
        if (!/\.(log|txt)$/i.test(file.name)) return 'Sunt acceptate doar fișiere .log și .txt.';
        if (file.size === 0) return 'Fișierul este gol.';
        if (file.size > MAX_FILE_BYTES) return 'Fișierul depășește 10 MB.';
        return null;
    }

    async function readJson(response) {
        try {
            return await response.json();
        } catch (error) {
            return null;
        }
    }
    function validateThresholds() {
        for (const input of document.querySelectorAll('.thresholds-grid input')) {
            if (!input.checkValidity()) {
                input.closest('details').open = true;
                input.reportValidity();
                return 'Verifică pragurile de detecție.';
            }
        }
        return null;
    }

    function appendThresholds(body) {
        for (const input of document.querySelectorAll('.thresholds-grid input')) {
            body.append(input.name, input.value);
        }
    }

    function resetThresholds() {
        for (const input of document.querySelectorAll('.thresholds-grid input')) {
            input.value = input.defaultValue;
        }
    }

    async function analyze(file) {
        const problem = validateFile(file) || validateThresholds();        if (problem) {
            setMessage(problem, 'error');
            return;
        }

        setBusy(true);
        setMessage(`Se analizează ${file.name} (${formatBytes(file.size)})…`, 'info');
        try {
            const body = new FormData();
            body.append('file', file, file.name);
            appendThresholds(body);
            const response = await fetch('api/analyze', { method: 'POST', body });
            const data = await readJson(response);
            if (!response.ok) {
                setMessage(data && data.error ? data.error : `Eroare (${response.status}).`, 'error');
                return;
            }
            render(data);
            setMessage(`Analiză încheiată: ${file.name}`, 'ok');
        } catch (error) {
            console.error(error);
            setMessage('Analiza nu s-a putut finaliza. Verifică dacă gateway-ul rulează.', 'error');
        } finally {
            setBusy(false);
            refreshStatus();
        }
    }

    async function analyzeSample() {
        setBusy(true);
        setMessage('Se încarcă fișierul de probă…', 'info');
        let file;
        try {
            const response = await fetch('api/sample', { cache: 'no-store' });
            if (!response.ok) throw new Error('sample');
            file = new File([await response.blob()], 'sample-access.log', { type: 'text/plain' });
        } catch (error) {
            setMessage('Fișierul de probă nu a putut fi încărcat.', 'error');
            setBusy(false);
            return;
        }
        setBusy(false);
        await analyze(file);
    }

    function setService(name, state) {
        const item = $(`svc-${name}`);
        item.dataset.state = state;
        item.querySelector('.svc-state').textContent = SERVICE_STATE_TEXT[state];
    }

    async function refreshStatus() {
        try {
            const response = await fetch('api/status', { cache: 'no-store' });
            if (!response.ok) throw new Error('status');
            const data = await response.json();
            setService('gateway', 'up');
            setService('parser', data.parser === 'UP' ? 'up' : 'down');
            setService('detector', data.detector === 'UP' ? 'up' : 'down');
        } catch (error) {
            setService('gateway', 'down');
            setService('parser', 'unknown');
            setService('detector', 'unknown');
        }
    }


    function render(data) {
        const alerts = Array.isArray(data.alerts) ? data.alerts : [];
        const stats = data.statistics;

        $('t-parsed').textContent = formatNumber(data.parsedLines);
        $('t-skipped').textContent = formatNumber(data.skippedLines);
        $('t-ips').textContent = formatNumber(stats.uniqueIps) ;
        $('t-alerts').textContent = formatNumber(alerts.length);
        $('t-alerts-sub').textContent = summarizeSeverities(alerts);

        renderAlerts(alerts);
        renderHours(stats.requestsByHour);
        renderStatus(stats.statusCounts);
        renderTopIps(stats.topIps, new Set(alerts.map((alert) => alert.ip)));
        $('results').hidden = false;
    }

    function summarizeSeverities(alerts) {
        if (alerts.length === 0) return 'nicio alertă';
        const counts = {};
        for (const alert of alerts) {
            counts[alert.severity] = (counts[alert.severity] || 0) + 1;
        }
        return ['HIGH', 'MEDIUM', 'LOW']
            .filter((severity) => counts[severity])
            .map((severity) => `${counts[severity]} ${severity}`)
            .join(' · ');
    }

    function renderAlerts(alerts) {
        const body = $('alerts-body');
        body.replaceChildren();
        for (const alert of alerts) {
            const severityCell = el('td');
            const severityClass = SEVERITY_CLASSES[alert.severity] || 'low';
            severityCell.append(el('span', `sev sev-${severityClass}`, alert.severity));

            const row = el('tr');
            row.append(
                severityCell,
                el('td', '', TYPE_LABELS[alert.type] || alert.type),
                el('td', 'mono', alert.ip),
                el('td', 'num', formatNumber(alert.eventCount)),
                el('td', 'mono', formatInterval(alert.firstSeen, alert.lastSeen)),
                el('td', '', alert.description));
            body.append(row);
        }
        $('alerts-table').hidden = alerts.length === 0;
        $('no-alerts').hidden = alerts.length > 0;
    }

    function renderHours(byHour) {
        const host = $('chart-hours');
        host.replaceChildren();
        const keys = Object.keys(byHour).sort();
        if (keys.length === 0) {
            host.append(el('p', 'empty', 'Fără date.'));
            return;
        }

        const width = 960, height = 260, left = 56, right = 10, top = 18, bottom = 34;
        const plotWidth = width - left - right;
        const plotHeight = height - top - bottom;
        const step = plotWidth / keys.length;
        const barWidth = Math.max(1, step * 0.72);
        const max = Math.max(...keys.map((key) => byHour[key]));
        const multiDay = new Set(keys.map((key) => key.slice(0, 10))).size > 1;
        const labelEvery = Math.ceil(keys.length / 16);

        const chart = svg('svg', {
            viewBox: `0 0 ${width} ${height}`,
            class: 'chart-svg',
            role: 'img',
            'aria-label': 'Cereri pe oră',
        });
        chart.append(
            svg('line', { x1: left, y1: top, x2: width - right, y2: top, class: 'axis' }),
            svg('line', { x1: left, y1: top + plotHeight, x2: width - right, y2: top + plotHeight, class: 'axis' }),
            tick(formatNumber(max), left - 6, top + 4, 'end'),
            tick('0', left - 6, top + plotHeight + 4, 'end'));

        keys.forEach((key, index) => {
            const value = byHour[key];
            const barHeight = max > 0 ? (value / max) * plotHeight : 0;
            const x = left + index * step + (step - barWidth) / 2;
            const bar = svg('rect', {
                x: x.toFixed(2),
                y: (top + plotHeight - barHeight).toFixed(2),
                width: barWidth.toFixed(2),
                height: barHeight.toFixed(2),
                class: 'bar',
            });
            bar.append(svgText('title', `${formatTime(key)} UTC · ${formatNumber(value)} cereri`));
            chart.append(bar);

            if (index % labelEvery === 0) {
                const label = multiDay
                    ? `${key.slice(8, 10)} ${key.slice(11, 13)}h`
                    : `${key.slice(11, 13)}h`;
                chart.append(tick(label, x + barWidth / 2, height - 10, 'middle'));
            }
        });
        host.append(chart);
    }

    function renderBars(host, items) {
        host.replaceChildren();
        if (items.length === 0) {
            host.append(el('p', 'empty', 'Fără date.'));
            return;
        }
        const max = Math.max(...items.map((item) => item.value));
        for (const item of items) {
            const percent = max > 0 ? Math.max(1, (item.value / max) * 100) : 0;
            const track = svg('svg', {
                viewBox: '0 0 100 8',
                preserveAspectRatio: 'none',
                class: 'bar-track',
                'aria-hidden': 'true',
            });
            track.append(svg('rect', {
                x: 0, y: 0, width: percent, height: 8, class: `bar-fill ${item.cls}`,
            }));

            const row = el('div', 'bar-row');
            row.append(el('span', 'bar-label', item.label), track,
                el('span', 'bar-value', formatNumber(item.value)));
            host.append(row);
        }
    }

    function renderStatus(counts) {
        const items = Object.keys(counts)
            .sort((a, b) => Number(a) - Number(b))
            .map((code) => {
                const group = Math.floor(Number(code) / 100);
                return {
                    label: code,
                    value: counts[code],
                    cls: [2, 3, 4, 5].includes(group) ? `status-${group}` : 'status-other',
                };
            });
        renderBars($('chart-status'), items);
    }

    function renderTopIps(topIps, flaggedIps) {
        renderBars($('chart-ips'), topIps.map((entry) => ({
            label: entry.ip,
            value: entry.requests,
            cls: flaggedIps.has(entry.ip) ? 'flagged' : 'plain',
        })));
    }


    $('upload-form').addEventListener('submit', (event) => {
        event.preventDefault();
        analyze($('file-input').files[0]);
    });
    $('sample-btn').addEventListener('click', analyzeSample);
    $('th-reset').addEventListener('click', resetThresholds);

    refreshStatus();
    setInterval(() => {
        if (!document.hidden) refreshStatus();
    }, STATUS_POLL_MS);
})();