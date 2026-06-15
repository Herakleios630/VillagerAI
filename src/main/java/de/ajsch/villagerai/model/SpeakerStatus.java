package de.ajsch.villagerai.model;

/**
 * Definiert den Status eines Sprechers (Villager) im Chief-System.
 * Ersetzt das bisherige unscharfe {@code isChief: boolean}.
 */
public enum SpeakerStatus {

    /** Lebender, aktiver Häuptling – besitzt alle Chief-Rechte und -Pflichten. */
    AKTIV_CHIEF,

    /** Verstorbener/abgelöster Ex-Chief – befindet sich in Trauerphase, kein aktiver Chief. */
    GEWESENER_CHIEF,

    /** Normaler Dorfbewohner – war nie Chief und hat keine Chief-Attribute. */
    NORMALER_DORFBEWOHNER
}