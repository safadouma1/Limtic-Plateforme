package com.projetweb.webapp.securite;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Le CIN est chiffré à l'écriture en base et déchiffré à la lecture (jamais stocké en clair). */
@Converter
public class CinConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String clair) {
        return clair == null || clair.isBlank() ? null : CinCrypto.get().chiffrer(clair.trim());
    }

    @Override
    public String convertToEntityAttribute(String valeur) {
        return valeur == null ? null : CinCrypto.get().dechiffrer(valeur);
    }
}
