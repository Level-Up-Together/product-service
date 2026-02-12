package io.pinkspider.leveluptogethermvp.userservice.core.converter;

import io.pinkspider.leveluptogethermvp.userservice.core.enums.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleConverter implements AttributeConverter<Role, Object> {

    @Override
    public Object convertToDatabaseColumn(Role role) {
        if (role == null) {
            return null;
        }

        // PostgreSQL enum 타입으로 캐스팅
        return role.name() + "::role";
    }

    @Override
    public Role convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }

        String roleStr = dbData.toString();
        return Role.valueOf(roleStr);
    }
}
