package com.archops.asset.type;

/**
 * How the workbench should open an asset when the user clicks Connect.
 * Dispatch lives in the type registry / {@code openAsset} helper — not scattered {@code switch(kind)}.
 */
public enum ConnectAction {
    /** WebSSH / IDE terminal (SERVER). */
    TERMINAL,
    /** Generic query shell (DATABASE, Redis, …). Panel may be deferred. */
    QUERY,
    /** Dedicated management page (future K8s console, …). */
    PAGE,
    /** No primary connect action — metadata / architecture only. */
    NONE
}
