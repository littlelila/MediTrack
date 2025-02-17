package ase.meditrack.controller;

import ase.meditrack.model.CreateValidator;
import ase.meditrack.model.UpdateValidator;
import ase.meditrack.model.dto.PreferencesDto;
import ase.meditrack.model.mapper.PreferencesMapper;
import ase.meditrack.service.PreferencesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/preferences")
@Slf4j
@CrossOrigin(origins = "http://localhost:4200", allowedHeaders = "*")
public class PreferencesController {
    private final PreferencesService service;
    private final PreferencesMapper mapper;

    public PreferencesController(PreferencesService service, PreferencesMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_admin')")
    public List<PreferencesDto> findAll() {
        log.info("Fetching preferences");
        return mapper.toDtoList(service.findAll());
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin') || "
            + "(hasAnyAuthority('SCOPE_dm', 'SCOPE_employee') && authentication.name == #id.toString())")
    public PreferencesDto findById(@PathVariable UUID id) {
        log.info("Fetching preferences with id: {}", id);
        return mapper.toDto(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_admin') || "
            + "(hasAnyAuthority('SCOPE_dm', 'SCOPE_employee') && @userService.isSameTeam(#principal, #dto.id()))")
    @ResponseStatus(HttpStatus.CREATED)
    public PreferencesDto create(@Validated(CreateValidator.class) @RequestBody PreferencesDto dto,
                                 Principal principal) {
        log.info("Creating preferences {}", dto.id());
        return mapper.toDto(service.create(mapper.fromDto(dto)));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_admin') || "
            + "(hasAnyAuthority('SCOPE_dm', 'SCOPE_employee') && @userService.isSameTeam(#principal, #dto.id()))")
    @ResponseStatus(HttpStatus.OK)
    public PreferencesDto update(@Validated(UpdateValidator.class) @RequestBody PreferencesDto dto,
                                 Principal principal) {
        log.info("Updating preferences {}", dto.id());
        return mapper.toDto(service.update(mapper.fromDto(dto)));
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin') || "
            + "(hasAnyAuthority('SCOPE_dm', 'SCOPE_employee')&& @userService.isSameTeam(#principal, #id))")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Principal principal) {
        log.info("Deleting preferences with id {}", id);
        service.delete(id);
    }
}
