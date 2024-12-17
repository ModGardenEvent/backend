package net.modgarden.backend.profile;

import net.modgarden.backend.event.Project;

import java.util.List;

public record User(long id,
                   String displayName,
                   List<Project> projects,
                   String modrinthUserId,
                   long discordId,
                   String minecraftUsername) {

}
