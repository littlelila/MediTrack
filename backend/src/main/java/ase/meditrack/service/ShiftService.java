package ase.meditrack.service;

import ase.meditrack.exception.NotFoundException;
import ase.meditrack.model.entity.MonthlyPlan;
import ase.meditrack.model.entity.Shift;
import ase.meditrack.model.entity.ShiftSwap;
import ase.meditrack.model.entity.ShiftType;
import ase.meditrack.model.entity.User;
import ase.meditrack.repository.MonthlyPlanRepository;
import ase.meditrack.repository.ShiftRepository;
import ase.meditrack.repository.ShiftTypeRepository;
import ase.meditrack.model.ShiftValidator;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ShiftService {
    private final ShiftRepository repository;
    private final UserService userService;
    private final ShiftTypeRepository shiftTypeRepository;

    private final MonthlyWorkDetailsService monthlyWorkDetailsService;
    private final MonthlyPlanRepository monthlyPlanRepository;
    private final ShiftValidator shiftValidator;
    private final ShiftSwapService shiftSwapService;

    public ShiftService(ShiftRepository repository, MonthlyWorkDetailsService monthlyWorkDetailsService,
                        ShiftTypeRepository shiftTypeRepository, MonthlyPlanRepository monthlyPlanRepository,
                        UserService userService, ShiftValidator shiftValidator, ShiftSwapService shiftSwapService) {
        this.repository = repository;
        this.monthlyWorkDetailsService = monthlyWorkDetailsService;
        this.shiftTypeRepository = shiftTypeRepository;
        this.monthlyPlanRepository = monthlyPlanRepository;
        this.userService = userService;
        this.shiftValidator = shiftValidator;
        this.shiftSwapService = shiftSwapService;
    }

    /**
     * Fetches all shifts from the database.
     *
     * @return List of all shift
     */
    public List<Shift> findAll() {
        return repository.findAll();
    }

    /**
     * Fetches all shifts from the current month from a user from the database.
     *
     * @param principal is current user
     * @return List of all shift from a current month from a user
     */
    public List<Shift> findAllByCurrentMonth(Principal principal) {
        User user = userService.getPrincipalWithTeam(principal);
        LocalDate today = LocalDate.now();
        LocalDate nextMonth = today.plusMonths(1).withDayOfMonth(1);

        List<UUID> users = new ArrayList<>();
        users.add(user.getId());
        return repository.findAllByUsersAndDateAfterAndDateBefore(users, today, nextMonth);
    }

    /**
     * Fetches a shift by id from the database.
     *
     * @param id the id of the shift
     * @return the shift
     */
    public Shift findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Could not find shift with id: " + id + "!"));
    }

    /**
     * Creates a shift in the database.
     *
     * @param shift the shift to create
     * @return the created shift
     */
    @Transactional
    public Shift create(Shift shift) {
        Optional<ShiftType> type = shiftTypeRepository.findById(shift.getShiftType().getId());
        Optional<MonthlyPlan> plan = monthlyPlanRepository.findById(shift.getMonthlyPlan().getId());
        if (type.isEmpty() || plan.isEmpty()) {
            throw new NotFoundException("Could not find shift type or plan of shift!");
        }
        shift.setShiftType(type.get());
        shift.setMonthlyPlan(plan.get());
        shiftValidator.validateShift(shift);
        Shift createdShift = repository.save(shift);
        monthlyWorkDetailsService.updateMonthlyWorkDetailsForShift(createdShift, null);
        return createdShift;
    }

    /**
     * Updates a shift in the database.
     *
     * @param shift the shift to update
     * @return the updated shift
     */
    @Transactional
    public Shift update(Shift shift) {
        // Fetch the shift with requestedShiftSwap using fetch join
        Shift dbShift = repository.findByIdWithShiftSwaps(shift.getId())
                .orElseThrow(() -> new NotFoundException("Shift not found"));

        // Ensure dbShift is not null
        if (dbShift == null) {
            throw new NotFoundException("Shift not found");
        }

        Optional<ShiftType> oldShiftType = shiftTypeRepository.findById(dbShift.getShiftType().getId());
        Optional<ShiftType> newShiftType = shiftTypeRepository.findById(shift.getShiftType().getId());
        Optional<MonthlyPlan> plan = monthlyPlanRepository.findById(shift.getMonthlyPlan().getId());
        if (newShiftType.isEmpty() || oldShiftType.isEmpty() || plan.isEmpty()) {
            throw new NotFoundException("Could not find shift type or plan of shift!");
        }

        dbShift.setShiftType(newShiftType.get());
        dbShift.setMonthlyPlan(plan.get());
        dbShift.setIsSick(shift.getIsSick());
        dbShift.setDate(shift.getDate());

        if (shift.getUsers() != null) {
            dbShift.setUsers(shift.getUsers());
        }

        shiftValidator.validateShift(dbShift);

        // Manually initialize the requestedShiftSwap list if not already done
        Hibernate.initialize(dbShift.getRequestedShiftSwap());

        // Check if requestedShiftSwap is initialized and delete shift swaps if not null
        if (dbShift.getRequestedShiftSwap() != null && !dbShift.getRequestedShiftSwap().isEmpty()) {
            for (ShiftSwap shiftSwap : dbShift.getRequestedShiftSwap()) {
                shiftSwapService.delete(shiftSwap.getId());
            }
        }

        // Save the updated shift
        Shift updatedShift = repository.save(dbShift);

        // Update monthly work details after shift is saved
        monthlyWorkDetailsService.updateMonthlyWorkDetailsForShift(updatedShift, oldShiftType.get());

        return updatedShift;
    }



    /**
     * Deletes a shift from the database.
     *
     * @param id the id of the shift to delete
     */
    public void delete(UUID id) {
        Optional<Shift> shift = repository.findById(id);
        if (shift.isEmpty()) {
            throw new NotFoundException("Could not find shift to delete!");
        }

        Optional<ShiftType> newShiftType = shiftTypeRepository.findById(shift.get().getShiftType().getId());
        Optional<MonthlyPlan> plan = monthlyPlanRepository.findById(shift.get().getMonthlyPlan().getId());
        if (newShiftType.isEmpty() || plan.isEmpty()) {
            throw new NotFoundException("Could not find shift type or plan of shift!");
        }

        monthlyWorkDetailsService.updateMonthlyWorkDetailsForDeletedShift(shift.get());
        repository.deleteById(id);
    }
}
