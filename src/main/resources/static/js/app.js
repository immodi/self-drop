(() => {
    "use strict";

    const body = document.body;
    const timeline = document.getElementById("timeline");
    const textForm = document.getElementById("text-form");
    const textarea = document.getElementById("text");
    const fileForm = document.getElementById("file-form");
    const fileInput = document.getElementById("file-input");
    const dropOverlay = document.getElementById("drop-overlay");
    const itemCount = document.getElementById("item-count");
    const connection = document.getElementById("connection");
    const connectionDot = document.getElementById("connection-dot");
    const connectionLabel = document.getElementById("connection-label");
    const toast = document.getElementById("toast");
    const toastDot = document.getElementById("toast-dot");
    const toastMessage = document.getElementById("toast-message");

    let refreshTimer = null;
    let toastTimer = null;
    let dragDepth = 0;

    // ---------------------------------------------------------------- timeline

    const updateCount = () => {
        if (!timeline || !itemCount) return;
        const count = timeline.querySelectorAll("article").length;
        itemCount.textContent = count === 1 ? "1 item" : count + " items";
    };

    const scheduleRefresh = () => {
        window.clearTimeout(refreshTimer);
        refreshTimer = window.setTimeout(() => {
            if (window.htmx) window.htmx.trigger(body, "item-changed");
        }, 50);
    };

    // ------------------------------------------------------------------- toast

    const showToast = (message, isError = false) => {
        if (!toast || !toastMessage || !toastDot) return;
        toastMessage.textContent = message;
        toastDot.className = "size-2 rounded-full " + (isError ? "bg-rose-400" : "bg-emerald-400");
        toast.classList.remove("hidden");
        toast.classList.add("flex");
        window.clearTimeout(toastTimer);
        toastTimer = window.setTimeout(() => {
            toast.classList.add("hidden");
            toast.classList.remove("flex");
        }, 2600);
    };

    // -------------------------------------------------------------- connection

    const setConnection = (state, label) => {
        if (!connection || !connectionDot || !connectionLabel) return;
        connection.dataset.state = state;
        connectionLabel.textContent = label;
        const color = state === "live"
            ? "bg-emerald-500"
            : state === "offline"
                ? "bg-rose-500"
                : "bg-amber-400";
        connectionDot.className = "size-2 rounded-full " + color;
    };

    if ("EventSource" in window) {
        const since = Number(body.dataset.since || "0") || 0;
        const source = new EventSource("/messages?since=" + encodeURIComponent(since));

        source.addEventListener("open", () => setConnection("live", "Live"));
        source.addEventListener("heartbeat", () => setConnection("live", "Live"));
        ["item-added", "item-updated", "item-deleted"].forEach((name) => {
            source.addEventListener(name, scheduleRefresh);
        });
        source.addEventListener("error", () => {
            const closed = source.readyState === EventSource.CLOSED;
            setConnection(closed ? "offline" : "connecting", closed ? "Offline" : "Reconnecting…");
        });
    } else {
        setConnection("offline", "Live updates unavailable");
    }

    // ------------------------------------------------------------------ upload

    const uploadFiles = (files) => {
        if (!fileForm || !fileInput || !files || files.length === 0) return;
        if (typeof DataTransfer === "undefined") {
            showToast("This browser cannot drop files here", true);
            return;
        }

        const transfer = new DataTransfer();
        Array.from(files).forEach((file) => transfer.items.add(file));
        fileInput.files = transfer.files;
        fileForm.requestSubmit();
    };

    fileInput?.addEventListener("change", () => {
        if (fileInput.files && fileInput.files.length > 0) fileForm.requestSubmit();
    });

    // ------------------------------------------------------------- drag & drop

    const hasFiles = (event) => Array.from(event.dataTransfer?.types || []).includes("Files");

    const showDrop = () => {
        if (!dropOverlay) return;
        dropOverlay.classList.remove("hidden");
        dropOverlay.classList.add("flex");
    };

    const hideDrop = () => {
        dragDepth = 0;
        if (!dropOverlay) return;
        dropOverlay.classList.add("hidden");
        dropOverlay.classList.remove("flex");
    };

    window.addEventListener("dragenter", (event) => {
        if (!hasFiles(event)) return;
        event.preventDefault();
        dragDepth += 1;
        showDrop();
    });

    window.addEventListener("dragover", (event) => {
        if (!hasFiles(event)) return;
        event.preventDefault();
        if (event.dataTransfer) event.dataTransfer.dropEffect = "copy";
    });

    window.addEventListener("dragleave", (event) => {
        if (!hasFiles(event)) return;
        dragDepth -= 1;
        if (dragDepth <= 0) hideDrop();
    });

    window.addEventListener("drop", (event) => {
        if (!hasFiles(event)) return;
        event.preventDefault();
        hideDrop();
        const files = event.dataTransfer?.files;
        if (files && files.length > 0) uploadFiles(files);
    });

    window.addEventListener("blur", hideDrop);

    // ------------------------------------------------------------------- paste

    document.addEventListener("paste", (event) => {
        const files = Array.from(event.clipboardData?.items || [])
            .filter((item) => item.kind === "file" && item.type.startsWith("image/"))
            .map((item) => item.getAsFile())
            .filter(Boolean);

        if (files.length === 0) return;
        event.preventDefault();
        uploadFiles(files);
    });

    // -------------------------------------------------------------------- copy

    const copyText = async (text) => {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return;
        }

        const helper = document.createElement("textarea");
        helper.value = text;
        helper.setAttribute("readonly", "");
        helper.style.position = "fixed";
        helper.style.opacity = "0";
        document.body.appendChild(helper);
        helper.select();
        const copied = document.execCommand("copy");
        helper.remove();
        if (!copied) throw new Error("copy failed");
    };

    document.addEventListener("click", async (event) => {
        const target = event.target instanceof Element
            ? event.target.closest("[data-copy], [data-copy-url]")
            : null;
        if (!target) return;

        let text = target.dataset.copy;
        if (!text && target.dataset.copyUrl) {
            text = new URL(target.dataset.copyUrl, window.location.href).href;
        }
        if (!text) return;

        try {
            await copyText(text);
            showToast("Copied to clipboard");
        } catch {
            showToast("Could not copy", true);
        }
    });

    // --------------------------------------------------------------- composer

    textarea?.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            if (textarea.value.trim().length > 0) textForm.requestSubmit();
        }
    });

    // ------------------------------------------------------------ htmx events

    body.addEventListener("htmx:afterRequest", (event) => {
        const elt = event.detail.elt;

        if (elt === textForm) {
            if (event.detail.successful) {
                textForm.reset();
                textarea?.focus();
            }
            return;
        }

        if (elt === fileForm) {
            if (event.detail.successful) {
                fileForm.reset();
                scheduleRefresh();
                showToast("Files added");
            }
            return;
        }

        if (event.detail.successful && elt?.hasAttribute("data-remove-on-success")) {
            elt.closest("article")?.remove();
            updateCount();
            showToast("Deleted");
        }
    });

    body.addEventListener("htmx:afterSwap", updateCount);
    body.addEventListener("htmx:afterSettle", updateCount);

    body.addEventListener("htmx:responseError", (event) => {
        let message = "Request failed";
        try {
            const problem = JSON.parse(event.detail.xhr.responseText);
            if (problem.detail) message = problem.detail;
        } catch {
            // Not a ProblemDetail response; keep the generic message.
        }
        showToast(message, true);
    });

    body.addEventListener("htmx:sendError", () => showToast("Network error", true));

    // ------------------------------------------------------------------- init

    updateCount();
    if (textarea && window.matchMedia("(min-width: 640px)").matches) {
        textarea.focus();
    }
})();
