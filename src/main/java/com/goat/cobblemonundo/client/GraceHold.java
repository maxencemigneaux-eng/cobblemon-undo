package com.goat.cobblemonundo.client;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShiftActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.goat.cobblemonundo.network.UndoRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.UUID;

/**
 * Mode 100 % client (quand le serveur n'a PAS le mod).
 *
 * Sans le mod côté serveur, un choix envoyé ne peut plus être repris : le serveur Cobblemon refuse
 * tout nouveau choix tant que le tour n'est pas joué. On fait donc l'inverse : quand tu valides,
 * le choix est retenu quelques secondes sur ton PC (avec un bouton Annuler et un bouton Envoyer),
 * puis envoyé normalement par le code de Cobblemon lui-même.
 */
public final class GraceHold {
    private record Hold(UUID battleId, long startMs, long durationMs) {
    }

    private static Hold hold;
    /** Vrai pendant qu'on rappelle nous-mêmes checkForFinishedChoosing() pour envoyer pour de vrai. */
    private static boolean sending;

    private GraceHold() {
    }

    /**
     * Appelé (via le mixin) au début de ClientBattle.checkForFinishedChoosing().
     * @return true si on retient le choix (Cobblemon ne l'envoie pas tout de suite)
     */
    public static boolean interceptSubmit(ClientBattle battle) {
        if (sending || battle.getSpectating()) {
            return false;
        }
        if (battle.getFirstUnansweredRequest() != null) {
            return false; // pas encore fini de choisir (combat double)
        }
        boolean pvp = isPvP(battle);
        if (pvp && ClientPlayNetworking.canSend(UndoRequestPayload.TYPE)) {
            return false; // le serveur a le mod : annulation façon Showdown, pas besoin de délai
        }
        UndoConfig config = UndoConfig.get();
        if (config.delaiSecondes <= 0) {
            return false;
        }
        if (!pvp && !config.aussiContrePnjEtSauvages) {
            return false;
        }

        // On ne retient que les vrais choix (attaque / switch). Un objet du sac ou une capture
        // est déjà validé par le serveur (ForcePassActionResponse) : on laisse partir tout de suite.
        boolean hasRealChoice = false;
        for (SingleActionRequest request : battle.getPendingActionRequests()) {
            ShowdownActionResponse response = request.getResponse();
            if (response instanceof MoveActionResponse
                    || response instanceof SwitchActionResponse
                    || response instanceof ShiftActionResponse) {
                hasRealChoice = true;
            } else if (!(response instanceof PassActionResponse)) {
                return false;
            }
        }
        if (!hasRealChoice) {
            return false;
        }

        hold = new Hold(battle.getBattleId(), System.currentTimeMillis(), Math.round(config.delaiSecondes * 1000.0));
        battle.setMustChoose(false); // masque le menu de choix pendant l'attente
        return true;
    }

    public static boolean isHolding() {
        return heldBattle() != null;
    }

    /** Secondes restantes avant l'envoi automatique. */
    public static double secondsLeft() {
        Hold h = hold;
        if (h == null) {
            return 0;
        }
        long left = h.startMs + h.durationMs - System.currentTimeMillis();
        return Math.max(0, left) / 1000.0;
    }

    /** Progression de 0 (on vient de valider) à 1 (envoi). */
    public static float progress() {
        Hold h = hold;
        if (h == null || h.durationMs <= 0) {
            return 1F;
        }
        float p = (System.currentTimeMillis() - h.startMs) / (float) h.durationMs;
        return Math.max(0F, Math.min(1F, p));
    }

    /** À appeler à chaque tick client : envoie le choix quand le délai est écoulé. */
    static void tick() {
        if (hold != null && heldBattle() != null && secondsLeft() <= 0) {
            sendNow();
        }
    }

    /** Bouton "Envoyer" : on n'attend pas la fin du délai. */
    public static void sendNow() {
        ClientBattle battle = heldBattle();
        hold = null;
        if (battle == null) {
            return;
        }
        sending = true;
        try {
            battle.checkForFinishedChoosing(); // le code de Cobblemon envoie le paquet normalement
        } finally {
            sending = false;
        }
    }

    /** Bouton "Annuler" : on efface les choix et le menu de Cobblemon réapparaît. */
    public static void cancel() {
        ClientBattle battle = heldBattle();
        hold = null;
        if (battle == null) {
            return;
        }
        // cancelLastAnsweredRequest() est la fonction "retour" de Cobblemon 1.8 :
        // elle gère aussi la Téracristal / Méga / Dynamax choisie ce tour-ci.
        int guard = battle.getPendingActionRequests().size() + 1;
        while (battle.getLastAnsweredRequest() != null && guard-- > 0) {
            battle.cancelLastAnsweredRequest();
        }
        // Filet de sécurité
        for (SingleActionRequest request : battle.getPendingActionRequests()) {
            ShowdownActionResponse response = request.getResponse();
            if (response != null && !(response instanceof PassActionResponse)) {
                request.setResponse(null);
            }
        }
        battle.setMustChoose(true); // BattleGUI réaffiche Attaque / Sac / Pokémon / Fuite
    }

    static void reset() {
        hold = null;
        sending = false;
    }

    /** Le combat concerné par l'attente, ou null si l'attente n'a plus de sens. */
    private static ClientBattle heldBattle() {
        Hold h = hold;
        if (h == null) {
            return null;
        }
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null
                || !h.battleId.equals(battle.getBattleId())
                || battle.getMustChoose()
                || battle.getFirstUnansweredRequest() != null) {
            hold = null;
            return null;
        }
        return battle;
    }

    private static boolean isPvP(ClientBattle battle) {
        for (ClientBattleSide side : battle.getSides()) {
            for (ClientBattleActor actor : side.getActors()) {
                if (actor.getType() != ActorType.PLAYER) {
                    return false;
                }
            }
        }
        return true;
    }
}
