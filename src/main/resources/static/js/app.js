(() => {
    "use strict";

    const body = document.body;
    const timeline = document.getElementById("timeline");
    const textForm = document.getElementById("text-form");
    const textarea = document.getElementById("text");
    const fileForm = document.getElementById("file-form");
    const fileInput = document.getElementById("file-input");
    const uploadMeter = document.getElementById("upload-meter");
    const uploadLabel = document.getElementById("upload-label");
    const uploadPercent = document.getElementById("upload-percent");
    const uploadTrack = document.getElementById("upload-track");
    const uploadBar = document.getElementById("upload-bar");
    const dropOverlay = document.getElementById("drop-overlay");
    const itemCount = document.getElementById("item-count");
    const connection = document.getElementById("connection");
    const connectionDot = document.getElementById("connection-dot");
    const connectionLabel = document.getElementById("connection-label");
    const sidebar = document.getElementById("sidebar");
    const sidebarBackdrop = document.getElementById("sidebar-backdrop");
    const sidebarOpen = document.getElementById("sidebar-open");
    const sidebarClose = document.getElementById("sidebar-close");
    const chatList = document.getElementById("chat-list");
    const lightbox = document.getElementById("lightbox");
    const lightboxImage = document.getElementById("lightbox-image");
    const lightboxClose = document.getElementById("lightbox-close");
    const toast = document.getElementById("toast");
    const toastDot = document.getElementById("toast-dot");
    const toastMessage = document.getElementById("toast-message");
    const settingsForm = document.getElementById("settings-form");
    const settingsUrl = document.getElementById("settings-url");
    const settingsError = document.getElementById("settings-error");
    const settingsSave = document.getElementById("settings-save");
    const settingsCurrent = document.getElementById("settings-current");
    const settingsCopy = document.getElementById("settings-copy");

    let refreshTimer = null;
    let toastTimer = null;
    let uploadHideTimer = null;
    let uploadProgress = 0;
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

    // ---------------------------------------------------------------- sidebar

    const desktopQuery = window.matchMedia("(min-width: 768px)");
    const SIDEBAR_KEY = "selfdrop:sidebar";

    const storedSidebarOpen = () => {
        try {
            return window.localStorage.getItem(SIDEBAR_KEY) !== "closed";
        } catch {
            return true;
        }
    };

    const setSidebar = (open, persist = true) => {
        if (!sidebar) return;
        const desktop = desktopQuery.matches;
        sidebar.dataset.open = open ? "true" : "false";

        sidebar.classList.toggle("-translate-x-full", !open);
        sidebar.classList.toggle("translate-x-0", open);
        if (desktop) {
            sidebar.classList.toggle("md:hidden", !open);
        } else {
            sidebar.classList.remove("md:hidden");
        }

        if (sidebarBackdrop) {
            sidebarBackdrop.classList.toggle("hidden", desktop || !open);
        }
        if (sidebarOpen) {
            sidebarOpen.classList.toggle("md:hidden", desktop && open);
            sidebarOpen.setAttribute("aria-expanded", String(open));
        }
        body.classList.toggle("overflow-hidden", !desktop && open);

        if (persist) {
            try {
                window.localStorage.setItem(SIDEBAR_KEY, open ? "open" : "closed");
            } catch {
                // Private mode can reject writes; the toggle still works per page.
            }
        }
    };

    setSidebar(desktopQuery.matches ? storedSidebarOpen() : false, false);

    sidebarOpen?.addEventListener("click", () => setSidebar(true));
    sidebarClose?.addEventListener("click", () => setSidebar(false));
    sidebarBackdrop?.addEventListener("click", () => setSidebar(false));

    desktopQuery.addEventListener("change", (event) => {
        setSidebar(event.matches ? storedSidebarOpen() : false, false);
    });

    // ----------------------------------------------------------- chat renaming

    const closeChatRename = (item) => {
        if (!item) return;
        const row = item.querySelector("[data-chat-row]");
        const form = item.querySelector("[data-rename-form]");
        row?.classList.remove("hidden");
        row?.classList.add("flex");
        form?.classList.add("hidden");
        form?.classList.remove("flex");
    };

    const closeAllChatRenames = () => {
        chatList?.querySelectorAll("li").forEach(closeChatRename);
    };

    chatList?.addEventListener("click", (event) => {
        if (!(event.target instanceof Element)) return;

        const cancel = event.target.closest("[data-chat-rename-cancel]");
        if (cancel) {
            closeChatRename(cancel.closest("li"));
            return;
        }

        const rename = event.target.closest("[data-chat-rename]");
        if (!rename) return;

        const item = rename.closest("li");
        const form = item?.querySelector("[data-rename-form]");
        const row = item?.querySelector("[data-chat-row]");
        const input = form?.querySelector("input[name='name']");
        if (!item || !form || !row) return;

        closeAllChatRenames();
        row.classList.add("hidden");
        row.classList.remove("flex");
        form.classList.remove("hidden");
        form.classList.add("flex");
        input?.focus();
        input?.select();
    });

    chatList?.addEventListener("keydown", (event) => {
        if (event.key !== "Escape" || !(event.target instanceof Element)) return;
        const form = event.target.closest("[data-rename-form]");
        if (!form) return;
        event.preventDefault();
        event.stopPropagation();
        closeChatRename(form.closest("li"));
    });

    // ------------------------------------------------------------- media viewer

    let lightboxLockedScroll = false;

    const openLightbox = (src, alt) => {
        if (!lightbox || !lightboxImage || !src) return;
        lightboxImage.src = src;
        lightboxImage.alt = alt || "";
        lightbox.classList.remove("hidden");
        lightbox.classList.add("flex");
        if (!body.classList.contains("overflow-hidden")) {
            body.classList.add("overflow-hidden");
            lightboxLockedScroll = true;
        }
        lightboxClose?.focus();
    };

    const closeLightbox = () => {
        if (!lightbox) return;
        lightbox.classList.add("hidden");
        lightbox.classList.remove("flex");
        if (lightboxImage) {
            lightboxImage.removeAttribute("src");
            lightboxImage.alt = "";
        }
        if (lightboxLockedScroll) {
            body.classList.remove("overflow-hidden");
            lightboxLockedScroll = false;
        }
    };

    const videoFullscreen = (video) => {
        try {
            if (typeof video.requestFullscreen === "function") {
                video.requestFullscreen().catch(() => {
                    if (typeof video.webkitEnterFullscreen === "function") {
                        video.webkitEnterFullscreen();
                    } else {
                        showToast("Fullscreen is not available", true);
                    }
                });
            } else if (typeof video.webkitEnterFullscreen === "function") {
                video.webkitEnterFullscreen();
            } else {
                showToast("Fullscreen is not available", true);
            }
        } catch {
            showToast("Fullscreen is not available", true);
        }
    };

    document.addEventListener("click", (event) => {
        if (!(event.target instanceof Element)) return;

        const fullscreen = event.target.closest("[data-video-fullscreen]");
        if (fullscreen) {
            const video = fullscreen.closest("article")?.querySelector("video");
            if (video) videoFullscreen(video);
            return;
        }

        if (event.target.closest("#lightbox-close") || event.target === lightbox) {
            closeLightbox();
            return;
        }

        const lightboxTrigger = event.target.closest("[data-lightbox-src]");
        if (!lightboxTrigger) return;
        event.preventDefault();
        openLightbox(lightboxTrigger.dataset.lightboxSrc, lightboxTrigger.dataset.lightboxAlt);
    });

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        if (lightbox && !lightbox.classList.contains("hidden")) {
            closeLightbox();
            return;
        }
        if (sidebar?.dataset.open !== "true") return;
        setSidebar(false);
        sidebarOpen?.focus();
    });

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

    if ("EventSource" in window && connection) {
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

    const formatBytes = (bytes) => {
        if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
        const units = ["B", "KB", "MB", "GB", "TB"];
        const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
        const value = bytes / Math.pow(1024, exponent);
        const decimals = value >= 10 || exponent === 0 ? 0 : 1;
        return value.toFixed(decimals) + " " + units[exponent];
    };

    const uploadSummary = () => {
        const files = fileInput?.files;
        if (!files || files.length === 0) return "Uploading…";
        const totalBytes = Array.from(files).reduce((sum, file) => sum + file.size, 0);
        const size = formatBytes(totalBytes);
        return files.length === 1
            ? files[0].name + " · " + size
            : files.length + " files · " + size;
    };

    const setUploadProgress = (percent) => {
        if (!uploadBar || !uploadPercent || !uploadTrack) return;
        uploadProgress = Math.max(uploadProgress, Math.min(100, percent));
        const rounded = Math.round(uploadProgress);
        uploadBar.style.width = uploadProgress + "%";
        uploadPercent.textContent = rounded + "%";
        uploadTrack.setAttribute("aria-valuenow", String(rounded));
    };

    const showUploadMeter = () => {
        if (!uploadMeter) return;
        window.clearTimeout(uploadHideTimer);
        uploadProgress = 0;
        if (uploadLabel) uploadLabel.textContent = uploadSummary();
        if (uploadBar) {
            uploadBar.className = "h-full w-0 rounded-full bg-indigo-600 transition-[width] duration-150 ease-out dark:bg-indigo-500";
        }
        uploadMeter.classList.remove("hidden");
        setUploadProgress(0);
    };

    const finishUploadMeter = (successful) => {
        if (!uploadMeter) return;
        if (successful) {
            setUploadProgress(100);
            if (uploadBar) {
                uploadBar.className = "h-full rounded-full bg-emerald-500 transition-[width] duration-150 ease-out";
            }
            if (uploadLabel) uploadLabel.textContent = "Upload complete";
        } else if (uploadBar) {
            uploadBar.className = "h-full rounded-full bg-rose-500 transition-[width] duration-150 ease-out";
            if (uploadLabel) uploadLabel.textContent = "Upload failed";
        }

        window.clearTimeout(uploadHideTimer);
        uploadHideTimer = window.setTimeout(() => {
            uploadMeter.classList.add("hidden");
        }, successful ? 900 : 1600);
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

    // ---------------------------------------------------------------- settings

    if (settingsForm && settingsUrl) {
        const endpoint = settingsForm.getAttribute("action") || "/settings/address";
        const saveLabel = settingsSave?.querySelector("span");

        const clearSettingsError = () => {
            if (!settingsError) return;
            settingsError.textContent = "";
            settingsError.classList.add("hidden");
        };

        const showSettingsError = (message) => {
            if (!settingsError) return;
            settingsError.textContent = message;
            settingsError.classList.remove("hidden");
        };

        settingsForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            clearSettingsError();

            if (!settingsForm.checkValidity()) {
                settingsForm.reportValidity();
                return;
            }

            if (settingsSave) settingsSave.disabled = true;
            if (saveLabel) saveLabel.textContent = "Saving…";

            try {
                const response = await fetch(endpoint, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json"
                    },
                    body: JSON.stringify({ url: settingsUrl.value.trim() })
                });

                const data = await response.json().catch(() => null);
                if (!response.ok) {
                    showSettingsError(
                        data?.errors?.url || data?.detail || "Could not save the address.");
                    return;
                }

                if (data?.url) {
                    settingsUrl.value = data.url;
                    if (settingsCurrent) settingsCurrent.textContent = data.url;
                    if (settingsCopy) settingsCopy.dataset.copy = data.url;
                }
                showToast("Address updated");
            } catch {
                showSettingsError("Network error. Is the server still running?");
            } finally {
                if (settingsSave) settingsSave.disabled = false;
                if (saveLabel) saveLabel.textContent = "Save address";
            }
        });
    }

    // ------------------------------------------------------------ htmx events

    body.addEventListener("htmx:beforeRequest", (event) => {
        if (event.detail.elt === fileForm) showUploadMeter();
    });

    body.addEventListener("htmx:xhr:progress", (event) => {
        if (event.detail.elt !== fileForm) return;
        const { loaded, total } = event.detail;
        if (!Number.isFinite(total) || total <= 0) return;
        setUploadProgress((loaded / total) * 100);
    });

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
            finishUploadMeter(event.detail.successful);
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
