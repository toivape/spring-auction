(function () {
    function pad(n) {
        return String(n).padStart(2, '0');
    }

    function render(el, remainingMs) {
        if (remainingMs <= 0) {
            el.textContent = 'closed';
            return;
        }
        var totalSeconds = Math.floor(remainingMs / 1000);
        var days = Math.floor(totalSeconds / 86400);
        var hours = Math.floor((totalSeconds % 86400) / 3600);
        var minutes = Math.floor((totalSeconds % 3600) / 60);
        var seconds = totalSeconds % 60;
        el.textContent = (days > 0 ? days + 'd ' : '') + pad(hours) + ':' + pad(minutes) + ':' + pad(seconds);
    }

    function tick() {
        document.querySelectorAll('[data-ends-at]').forEach(function (el) {
            var endsAt = new Date(el.getAttribute('data-ends-at')).getTime();
            render(el, endsAt - Date.now());
        });
    }

    tick();
    setInterval(tick, 1000);
})();
