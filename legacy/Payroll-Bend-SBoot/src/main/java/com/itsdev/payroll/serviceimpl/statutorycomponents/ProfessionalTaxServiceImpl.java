package com.itsdev.payroll.serviceimpl.statutorycomponents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itsdev.payroll.controller.employee.EmployyePortalContoller;
import com.itsdev.payroll.dto.statutorycomponents.ProfessionalTaxDTO;
import com.itsdev.payroll.dto.statutorycomponents.SlabDetailDTO;
import com.itsdev.payroll.dto.statutorycomponents.TaxSlabUpdateRequest;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.WorkLocation;
import com.itsdev.payroll.entity.statutorycomponents.OrgPTOverride;
import com.itsdev.payroll.entity.statutorycomponents.PTHistory;
import com.itsdev.payroll.entity.statutorycomponents.ProfessionalTax;
import com.itsdev.payroll.entity.statutorycomponents.SlabDetail;
import com.itsdev.payroll.entity.statutorycomponents.SlabRateConfiguration;
import com.itsdev.payroll.entity.statutorycomponents.TaxSlabDetailHistory;
import com.itsdev.payroll.mapper.statutorycomponents.ProfessionalTaxMapper;
import com.itsdev.payroll.repository.MasterConfigRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.statutorycomponents.OrgPTOverrideRepository;
import com.itsdev.payroll.repository.statutorycomponents.PTHistoryRepository;
import com.itsdev.payroll.repository.statutorycomponents.ProfessionalTaxRepository;
import com.itsdev.payroll.repository.statutorycomponents.TaxSlabDetailHistoryRepository;
import com.itsdev.payroll.service.statutorycomponents.ProfessionalTaxService;
import com.itsdev.payroll.util.ProfessionalTaxUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfessionalTaxServiceImpl implements ProfessionalTaxService {

	private final ProfessionalTaxRepository professionalTaxRepository;
	private final OrganizationRepository organizationRepository;
	private final MasterConfigRepository masterConfigRepository;
	private final TaxSlabDetailHistoryRepository taxSlabDetailHistoryRepository;
	private final OrgPTOverrideRepository orgPTOverrideRepository;
    private final ObjectMapper objectMapper;
    private final PTHistoryRepository ptHistoryRepository;
	
	private static final List<String> DEFAULT_REVERSED_MONTHS = Arrays.asList(
		    "December", "November", "October", "September", "August", "July",
		    "June", "May", "April", "March", "February", "January"
		);


	private static final Logger log = LoggerFactory.getLogger(ProfessionalTaxServiceImpl.class);

	public ProfessionalTaxServiceImpl(ProfessionalTaxRepository professionalTaxRepository,
			OrganizationRepository organizationRepository, MasterConfigRepository masterConfigRepository,
			TaxSlabDetailHistoryRepository taxSlabDetailHistoryRepository, OrgPTOverrideRepository orgPTOverrideRepository,
			ObjectMapper objectMapper, PTHistoryRepository ptHistoryRepository) {
		this.professionalTaxRepository = professionalTaxRepository;
		this.organizationRepository = organizationRepository;
		this.masterConfigRepository = masterConfigRepository;
		this.taxSlabDetailHistoryRepository = taxSlabDetailHistoryRepository;
		this.orgPTOverrideRepository = orgPTOverrideRepository;
		this.objectMapper = objectMapper;
		this.ptHistoryRepository = ptHistoryRepository;
	}

	private String generateTaxId() {
		Random random = new Random();
		long number = 1000000000L + (long) (random.nextDouble() * 9000000000L);
		return String.valueOf(number);
	}

	private String generateTaxRateSettingsId() {
		Random random = new Random();
		long number = 1000000000L + (long) (random.nextDouble() * 9000000000L);
		return String.valueOf(number);
	}

//	@Override
//	public ProfessionalTaxDTO createDefaultTax(Organization organization, String state) {
//		boolean supported = ProfessionalTaxUtil.isProfessionalTaxSupported(state);
//
//		ProfessionalTax tax = new ProfessionalTax();
//		tax.setTaxId(generateTaxId());
//		tax.setOrganization(organization);
//		tax.setState(state);
//		tax.setProfessionalTaxSupported(supported);
//
//		if (supported) {
//			// Fetch slab_details config from MasterConfig
//			MasterConfig master = masterConfigRepository.findByComponentName("slabDetails")
//					.orElseThrow(() -> new RuntimeException("Master config 'slabDetails' not found"));
//
//			JsonNode slabConfig = master.getConfigData();
//
//			List<SlabDetail> slabDetails = new ArrayList<>();
//			List<SlabDetail> rateSlabs = new ArrayList<>();
//
//			if (slabConfig.isArray()) {
//				for (JsonNode node : slabConfig) {
//					// Slab for ProfessionalTax (direct)
//					SlabDetail slab = new SlabDetail();
//					slab.setProfessionalTax(tax);
//					slab.setPayAmount(node.path("payAmount").asDouble(0));
//					slab.setFemaleExempted(node.path("isFemaleExempted").asBoolean(false));
//					slab.setStartAmount(node.path("startAmount").asDouble(0));
//					slab.setEndAmount(node.path("endAmount").asDouble(0));
//
//					List<String> deductionMonths = new ArrayList<>();
//					node.path("deductionMonths").forEach(month -> deductionMonths.add(month.asText()));
//					slab.setDeductionMonths(deductionMonths);
//
//					slabDetails.add(slab);
//
//					// Clone slab for SlabRateConfiguration
//					SlabDetail rateSlab = new SlabDetail();
//					rateSlab.setPayAmount(node.path("payAmount").asDouble(0));
//					rateSlab.setFemaleExempted(node.path("isFemaleExempted").asBoolean(false));
//					rateSlab.setStartAmount(node.path("startAmount").asDouble(0));
//					rateSlab.setEndAmount(node.path("endAmount").asDouble(0));
//					rateSlab.setDeductionMonths(deductionMonths);
//					rateSlabs.add(rateSlab);
//				}
//			}
//
//			tax.setSlabDetails(slabDetails);
//
//			// Create default SlabRateConfiguration
//			SlabRateConfiguration rateConfig = new SlabRateConfiguration();
//			rateConfig.setTaxRateSettingsId(generateTaxRateSettingsId());
//			rateConfig.setTaxConfigurationFrequency("monthly"); // default, can adjust
//			rateConfig.setGrossSalaryConfigurationFrequency("monthly"); // default
//			rateConfig.setDeductionFrequency("monthly"); // default
//			rateConfig.setEffectiveFrom(LocalDate.now().toString());
//			rateConfig.setEffectiveTo(null);
//			rateConfig.setActive(true);
//			rateConfig.setEditable(true);
//			rateConfig.setProfessionalTax(tax);
//
//			// Link slabs with this config
//			rateSlabs.forEach(slab -> slab.setSlabRateConfiguration(rateConfig));
//			rateConfig.setSlabDetails(rateSlabs);
//
//			tax.setSlabRateConfigurations(List.of(rateConfig));
//		}
//
//		ProfessionalTax saved = professionalTaxRepository.save(tax);
//		return ProfessionalTaxMapper.toDTO(saved);
//	}


	@Override
	public ProfessionalTaxDTO createDefaultTax(Organization organization, String state) {
		boolean supported = ProfessionalTaxUtil.isProfessionalTaxSupported(state);

		ProfessionalTax tax = new ProfessionalTax();
		tax.setTaxId(generateTaxId());
		tax.setOrganization(organization);
		tax.setState(state);
		tax.setProfessionalTaxSupported(supported);
		tax.setEffectiveFrom(LocalDate.now().toString());

		if (supported) {
			String method = "createDefaultTax";
			String normalizedState = (state != null) ? state.trim() : null;

			// 1) Load the single master row for all PT slabs
			MasterConfig master = masterConfigRepository
					.findByComponentName("slabDetails")
					.orElseThrow(() -> new RuntimeException(
							"Master config 'slabDetails' not found"));

			JsonNode allStatesNode = master.getConfigData();
			if (allStatesNode == null || !allStatesNode.isObject()) {
				throw new RuntimeException("Invalid JSON structure in masterConfig.slabDetails. Expected an object with state keys.");
			}

			// 2) Pick the slabs for the current state, e.g. allStates["Maharashtra"]
			JsonNode slabConfig = allStatesNode.path(normalizedState);
			if (slabConfig.isMissingNode() || !slabConfig.isArray()) {
				throw new RuntimeException("No slab config found in masterConfig for state=" + normalizedState);
			}

			List<SlabDetail> slabDetails = new ArrayList<>();

			// 3) Convert JSON array -> List<SlabDetail>
			for (JsonNode node : slabConfig) {
				SlabDetail slab = new SlabDetail();
				slab.setProfessionalTax(tax);

				slab.setPayAmount(node.path("payAmount").asDouble(0));
				slab.setFemaleExempted(node.path("isFemaleExempted").asBoolean(false));
				slab.setStartAmount(node.path("startAmount").asDouble(0));
				slab.setEndAmount(node.path("endAmount").asDouble(0));

				// deductionMonths: [] = all months, ["February"] = only Feb, etc.
				List<String> deductionMonths = new ArrayList<>();
				JsonNode monthsNode = node.path("deductionMonths");
				if (monthsNode.isArray()) {
					monthsNode.forEach(month -> deductionMonths.add(month.asText()));
				}
				slab.setDeductionMonths(deductionMonths);

				slab.setDefaultFromMaster(true);

				slabDetails.add(slab);
			}

			tax.setSlabDetails(slabDetails);
		}

		ProfessionalTax saved = professionalTaxRepository.save(tax);
		return ProfessionalTaxMapper.toDTO(saved);
	}

//    @Override
//    public ProfessionalTaxDTO getProfessionalTax(String organizationId) {
//        Organization org = organizationRepository.findByOrganizationId(organizationId)
//                .orElseThrow(() -> new RuntimeException("Organization not found"));
//
//        ProfessionalTax professionalTax = professionalTaxRepository.findByOrganization(org)
//                .orElseThrow(() -> new RuntimeException("Professional tax not found"));
//
//        return ProfessionalTaxMapper.toDTO(professionalTax);
//    }

	@Override
	@Transactional
	public ProfessionalTaxDTO updateProfessionalTax(
			String organizationId,
			String state,
			ProfessionalTaxDTO dto) {

		System.out.println("=================================");
		System.out.println("Updating PT Override");
		System.out.println("State (taxId): " + state);
		System.out.println("OrganizationId: " + organizationId);
		System.out.println("RegistrationNumber: " + dto.getRegistrationNumber());
		System.out.println("=================================");

		// 🔹 Step 1: Fetch existing override or create new one
		OrgPTOverride override = orgPTOverrideRepository
				.findByOrganizationIdAndState(organizationId, state)
				.orElseGet(() -> {
					OrgPTOverride newOverride = new OrgPTOverride();
					newOverride.setOrganizationId(organizationId);
					newOverride.setState(state);
					return newOverride;
				});

		// 🔹 Step 2: Update registration number
		override.setRegistrationNumber(dto.getRegistrationNumber());

		// Optional audit fields
		override.setChangedAt(LocalDateTime.now());
		override.setChangedBy("SYSTEM");

		// 🔹 Step 3: Save override
		orgPTOverrideRepository.save(override);

		// 🔹 Step 4: Return updated data using master + override logic
		return getAllProfessionalTaxes(organizationId)
				.stream()
				.filter(x -> x.getState().equals(state))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("State not found after update"));
	}


	/*
	@Override
	@Transactional
	public ProfessionalTaxDTO updateSlabAndEffectiveDate(String organizationId, String taxId,
			TaxSlabUpdateRequest dto) {

		final String method = "updateSlabAndEffectiveDate";

		log.info("[{}] 📥 Request received | orgId={}, taxId={}, effectiveFrom={}", method, organizationId, taxId,
				dto.getEffectiveFrom());

		// 1️⃣ Fetch organization
		Organization org = organizationRepository.findByOrganizationId(organizationId).orElseThrow(() -> {
			log.error("[{}] ❌ Organization not found | orgId={}", method, organizationId);
			return new RuntimeException("Organization not found");
		});

		// 2️⃣ Fetch professional tax
		ProfessionalTax professionalTax = professionalTaxRepository.findByTaxIdAndOrganization(taxId, org)
				.orElseThrow(() -> {
					log.error("[{}] ❌ ProfessionalTax not found | taxId={}", method, taxId);
					return new RuntimeException("Professional tax not found");
				});

		// IMPORTANT: set effectiveFrom (prefer per-slab, fallback to dto effectiveFrom)
		// if your Slab DTO contains effectiveFrom, use s.getEffectiveFrom() else use dto.getEffectiveFrom()
		String effective = (dto.getEffectiveFrom() != null && !dto.getEffectiveFrom().isEmpty())
				? dto.getEffectiveFrom()
				: dto.getEffectiveFrom();
		professionalTax.setEffectiveFrom(effective);

		log.info("[{}] 📌 Current slabs loaded | count={}", method, professionalTax.getSlabDetails().size());

		professionalTax.getSlabDetails().forEach(s -> {
			log.info("[{}]   OLD → id={}, start={}, end={}, payAmount={}", method, s.getId(), s.getStartAmount(),
					s.getEndAmount(), s.getPayAmount());
		});

		log.info("[{}] 📌 Incoming new slab count={}", method, dto.getSlabDetails().size());
		dto.getSlabDetails().forEach(s -> {
			log.info("[{}]   NEW → id={}, start={}, end={}, payAmount={}", method, s.getId(), s.getStartAmount(),
					s.getEndAmount(), s.getPayAmount());
		});

		// 3️⃣ Capture history (insert/update/delete)
		log.info("[{}] 🕵️ Detecting slab changes...", method);
		captureSlabChanges(professionalTax, dto);
		log.info("[{}] 📝 Slab changes captured successfully", method);

		// 4️⃣ Update effective date
		log.info("[{}] 🕒 Updating effectiveFrom for {} slab configurations → {}", method,
				professionalTax.getSlabRateConfigurations().size(), dto.getEffectiveFrom());

		professionalTax.getSlabRateConfigurations().forEach(cfg -> cfg.setEffectiveFrom(dto.getEffectiveFrom()));

		// 5️⃣ Replace slab details
		log.info("[{}] 🔄 Replacing old slabs with new slabs", method);

		professionalTax.getSlabDetails().clear();

		professionalTax.getSlabDetails().addAll(dto.getSlabDetails().stream().map(s -> {
			SlabDetail slab = new SlabDetail();
			slab.setStartAmount(s.getStartAmount());
			slab.setEndAmount(s.getEndAmount());
			slab.setPayAmount(s.getPayAmount());
			slab.setFemaleExempted(s.isFemaleExempted());
			slab.setDefaultFromMaster(s.getDefaultFromMaster());
			
		     List<String> months =
		                (s.getDeductionMonths() == null || s.getDeductionMonths().isEmpty())
		                ? new ArrayList<>(DEFAULT_REVERSED_MONTHS)
		                : new ArrayList<>(s.getDeductionMonths());

		            slab.setDeductionMonths(months);




			slab.setProfessionalTax(professionalTax);
			return slab;
		}).collect(Collectors.toList()));
		
		
	
		log.info("[{}] 🧱 New slabs applied | newCount={}", method, professionalTax.getSlabDetails().size());

		// 6️⃣ Save entity
		ProfessionalTax saved = professionalTaxRepository.save(professionalTax);
		log.info("[{}] ✅ Update complete | taxId={}, slabCount={}", method, saved.getTaxId(),
				saved.getSlabDetails().size());

		return ProfessionalTaxMapper.toDTO(saved);
	}  */
	
	@Override
	@Transactional
	public ProfessionalTaxDTO updateSlabAndEffectiveDate(
	        String organizationId,
	        String taxId,
	        TaxSlabUpdateRequest request) {

	    final String method = "updateSlabAndEffectiveDate";

	    log.info("[{}] ▶ Starting update process | orgId={}, taxId={}, effectiveFrom={}", 
	            method, organizationId, taxId, request.getEffectiveFrom());

	    // 1️⃣ Load Override or Create
	    OrgPTOverride override = orgPTOverrideRepository
	            .findByOrganizationIdAndState(organizationId, taxId)
	            .orElseGet(() -> {
	                log.info("[{}] ℹ No override found → creating new one | taxId={}", method, taxId);
	                OrgPTOverride o = new OrgPTOverride();
	                o.setOrganizationId(organizationId);
	                o.setState(taxId);
	                return o;
	            });

	    log.info("[{}] ✅ Loaded override | currentOverrideExists={}", method, override.getId() != null);

	    // 2️⃣ Store OLD JSON
	    JsonNode oldJson = override.getOverrideJson() != null ? override.getOverrideJson().deepCopy() : objectMapper.createArrayNode();
	    log.info("[{}] 📦 Stored old JSON for history | oldJsonSize={}", method, oldJson.size());

	    // 3️⃣ Update effective date
	    if (request.getEffectiveFrom() != null && !request.getEffectiveFrom().isEmpty()) {
	        try {
	            LocalDate date = LocalDate.parse(request.getEffectiveFrom());
	            override.setEffectiveFrom(date);
	            log.info("[{}] 📅 Updated effectiveFrom = {}", method, date);
	        } catch (Exception ex) {
	            log.error("[{}] ❌ Invalid effectiveFrom date format: {}", method, request.getEffectiveFrom());
	            throw new RuntimeException("Invalid effectiveFrom date format. Expected yyyy-MM-dd");
	        }
	    } else {
	        log.info("[{}] ⚠ No effectiveFrom provided, keeping existing value", method);
	    }

	    // 4️⃣ Build NEW JSON
	    ArrayNode updatedSlabsJson = objectMapper.createArrayNode();
	    for (SlabDetailDTO slab : request.getSlabDetails()) {
	        ObjectNode json = objectMapper.createObjectNode();
	        json.put("startAmount", slab.getStartAmount());
	        json.put("endAmount", slab.getEndAmount());
	        json.put("payAmount", slab.getPayAmount());
	        json.put("isFemaleExempted", slab.isFemaleExempted());

	        ArrayNode months = objectMapper.createArrayNode();
	        if (slab.getDeductionMonths() != null) {
	            slab.getDeductionMonths().forEach(months::add);
	        }
	        json.set("deductionMonths", months);

	        updatedSlabsJson.add(json);

	        log.info("[{}] ➡ Processed slab | start={}, end={}, pay={}", 
	                 method, slab.getStartAmount(), slab.getEndAmount(), slab.getPayAmount());
	    }

	    log.info("[{}] 🧩 Total slabs updated = {}", method, updatedSlabsJson.size());
	    override.setOverrideJson(updatedSlabsJson);

	    // 5️⃣ Save override
	    OrgPTOverride saved = orgPTOverrideRepository.save(override);
	    log.info("[{}] 💾 Override saved successfully | id={}, state={}", method, saved.getId(), saved.getState());

	    // 6️⃣ Save history separately
	    savePTHistory(organizationId, taxId, oldJson, updatedSlabsJson, "SYSTEM");
	    log.info("[{}] 📝 History saved for orgId={}, state={}", method, organizationId, taxId);

	    // 7️⃣ Return updated DTO
	    List<ProfessionalTaxDTO> list = this.getAllProfessionalTaxes(organizationId);
	    ProfessionalTaxDTO finalResult = list.stream()
	            .filter(x -> x.getState().equals(taxId))
	            .findFirst()
	            .orElseThrow(() -> new RuntimeException("Unexpected: state not found after save"));

	    log.info("[{}] ✔ Returning updated DTO | state={}, effectiveFrom={}", 
	             method, finalResult.getState(), finalResult.getEffectiveFrom());

	    return finalResult;
	}

	
	private void savePTHistory(String organizationId, String state, JsonNode oldJson, JsonNode newJson, String changedBy) {
	    final String method = "savePTHistory";

	    PTHistory history = new PTHistory();
	    history.setOrganizationId(organizationId);
	    history.setState(state);
	    history.setOldJson(oldJson != null ? oldJson : objectMapper.createArrayNode());
	    history.setNewJson(newJson != null ? newJson : objectMapper.createArrayNode());

	    // Determine operation
	    String operation = (oldJson == null || oldJson.size() == 0) ? "INSERT" : "UPDATE";
	    history.setOperation(operation);

	    history.setChangedAt(LocalDateTime.now());
	    history.setChangedBy(changedBy != null ? changedBy : "SYSTEM");

	    ptHistoryRepository.save(history);

	    log.info("[{}] 📝 PT history saved | orgId={}, state={}, operation={}", method, organizationId, state, operation);
	}



	private void captureSlabChanges(ProfessionalTax professionalTax, TaxSlabUpdateRequest dto) {

		final String METHOD = "captureSlabChanges";

		log.info("[{}] 🔍 Starting slab history scan | taxId={} | oldCount={} | newCount={}", METHOD,
				professionalTax.getTaxId(), professionalTax.getSlabDetails().size(), dto.getSlabDetails().size());

		List<SlabDetail> oldSlabsList = professionalTax.getSlabDetails();

		Map<Long, SlabDetail> oldSlabs = oldSlabsList.stream().filter(s -> s.getId() != null)
				.collect(Collectors.toMap(SlabDetail::getId, s -> s));

		Set<Long> newIds = dto.getSlabDetails().stream().map(SlabDetailDTO::getId).filter(Objects::nonNull)
				.collect(Collectors.toSet());

// 🔴 DELETE DETECTION
		oldSlabs.values().forEach(old -> {
			if (!newIds.contains(old.getId())) {
				log.warn("[{}] 🗑️ DELETE detected | id={}, start={}, end={}, pay={}", METHOD, old.getId(),
						old.getStartAmount(), old.getEndAmount(), old.getPayAmount());
				saveDeleteHistory(professionalTax, old);
			}
		});

// 🟡 INSERT + UPDATE DETECTION
		for (SlabDetailDTO newSlab : dto.getSlabDetails()) {

// INSERT
			if (newSlab.getId() == null) {
				log.info("[{}] ➕ INSERT detected | start={}, end={}, pay={}", METHOD, newSlab.getStartAmount(),
						newSlab.getEndAmount(), newSlab.getPayAmount());
				saveInsertHistory(professionalTax, newSlab);
				continue;
			}

			SlabDetail old = oldSlabs.get(newSlab.getId());
			if (old == null)
				continue;

			boolean changed = !Objects.equals(old.getStartAmount(), newSlab.getStartAmount())
					|| !Objects.equals(old.getEndAmount(), newSlab.getEndAmount())
					|| !Objects.equals(old.getPayAmount(), newSlab.getPayAmount());

// UPDATE
			if (changed) {
				log.info(
						"[{}] ✏️ UPDATE detected | id={} | old(start={}, end={}, pay={}) → new(start={}, end={}, pay={})",
						METHOD, newSlab.getId(), old.getStartAmount(), old.getEndAmount(), old.getPayAmount(),
						newSlab.getStartAmount(), newSlab.getEndAmount(), newSlab.getPayAmount());
				saveUpdateHistory(professionalTax, old, newSlab);
			}
		}

		log.info("[{}] ✅ History scan completed for taxId={}", METHOD, professionalTax.getTaxId());
	}

	private void saveInsertHistory(ProfessionalTax tax, SlabDetailDTO dto) {

	    final String METHOD = "saveInsertHistory";

	    log.info("[{}] Saving INSERT history | taxId={}, start={}, end={}, pay={}",
	            METHOD,
	            tax.getTaxId(),
	            dto.getStartAmount(),
	            dto.getEndAmount(),
	            dto.getPayAmount()
	    );

	    TaxSlabDetailHistory h = new TaxSlabDetailHistory();
	    h.setTaxId(tax.getTaxId());
	    h.setOrganizationId(tax.getOrganization().getOrganizationId());

	    h.setStartAmount(dto.getStartAmount());
	    h.setEndAmount(dto.getEndAmount());
	    h.setPayAmount(dto.getPayAmount());
	    h.setActionType("INSERT");

	    taxSlabDetailHistoryRepository.save(h);

	    log.debug("[{}] INSERT history saved successfully", METHOD);
	}

	private void saveDeleteHistory(ProfessionalTax tax, SlabDetail old) {

	    final String METHOD = "saveDeleteHistory";

	    log.info("[{}] Saving DELETE history | taxId={}, old(start={}, end={}, pay={})",
	            METHOD,
	            tax.getTaxId(),
	            old.getStartAmount(),
	            old.getEndAmount(),
	            old.getPayAmount()
	    );

	    TaxSlabDetailHistory h = new TaxSlabDetailHistory();
	    h.setTaxId(tax.getTaxId());
	    h.setOrganizationId(tax.getOrganization().getOrganizationId());

	    h.setOldStartAmount(old.getStartAmount());
	    h.setOldEndAmount(old.getEndAmount());
	    h.setOldPayAmount(old.getPayAmount());
	    h.setActionType("DELETE");

	    taxSlabDetailHistoryRepository.save(h);

	    log.info("[{}] DELETE history saved successfully", METHOD);
	}


	private void saveUpdateHistory(ProfessionalTax tax, SlabDetail old, SlabDetailDTO updated) {

	    final String METHOD = "saveUpdateHistory";

	    log.info("[{}] Saving UPDATE history | taxId={} | old(start={}, end={}, pay={}) → new(start={}, end={}, pay={})",
	            METHOD,
	            tax.getTaxId(),
	            old.getStartAmount(), old.getEndAmount(), old.getPayAmount(),
	            updated.getStartAmount(), updated.getEndAmount(), updated.getPayAmount()
	    );

	    TaxSlabDetailHistory h = new TaxSlabDetailHistory();
	    h.setTaxId(tax.getTaxId());
	    h.setOrganizationId(tax.getOrganization().getOrganizationId());

	    h.setOldStartAmount(old.getStartAmount());
	    h.setOldEndAmount(old.getEndAmount());
	    h.setOldPayAmount(old.getPayAmount());

	    h.setStartAmount(updated.getStartAmount());
	    h.setEndAmount(updated.getEndAmount());
	    h.setPayAmount(updated.getPayAmount());

	    h.setActionType("UPDATE");

	    taxSlabDetailHistoryRepository.save(h);

	    log.info("[{}] UPDATE history saved successfully", METHOD);
	}
	
	/*
	@Override
	@Transactional
	public ProfessionalTaxDTO resetToDefaultSlabs(String organizationId, String taxId) {

	    final String method = "resetToDefaultSlabs";
	    log.info("[{}] 📥 Starting reset process | orgId={}, taxId={}", method, organizationId, taxId);

	    // 1️⃣ Fetch organization
	    Organization org = organizationRepository.findByOrganizationId(organizationId)
	            .orElseThrow(() -> new RuntimeException("Organization not found"));

	    // 2️⃣ Fetch ProfessionalTax
	    ProfessionalTax professionalTax = professionalTaxRepository
	            .findByTaxIdAndOrganization(taxId, org)
	            .orElseThrow(() -> new RuntimeException("Professional Tax not found"));

	    log.info("[{}] Current slabCount={}", method, professionalTax.getSlabDetails().size());

	    // 3️⃣ Filter only default slabs
	    List<SlabDetail> defaultSlabs = professionalTax.getSlabDetails().stream()
	            .filter(SlabDetail::getDefaultFromMaster)
	            .peek(s -> log.info("[{}] Keeping default slab → id={}, start={}, end={}",
	                    method, s.getId(), s.getStartAmount(), s.getEndAmount()))
	            .collect(Collectors.toList());

	    log.info("[{}] Default slabs found={}", method, defaultSlabs.size());

	    // 4️⃣ Clear and restore only default slabs
	    professionalTax.getSlabDetails().clear();

	    defaultSlabs.forEach(s -> s.setProfessionalTax(professionalTax));
	    professionalTax.getSlabDetails().addAll(defaultSlabs);

	    log.info("[{}] After reset slabCount={}", method, professionalTax.getSlabDetails().size());

	    // 5️⃣ Save
	    ProfessionalTax saved = professionalTaxRepository.save(professionalTax);

	    log.info("[{}] Reset completed | finalCount={}", method, saved.getSlabDetails().size());

	    return ProfessionalTaxMapper.toDTO(saved);
	} */


/*
	@Override
	public List<ProfessionalTaxDTO> getAllProfessionalTaxes(String organizationId) {
		Organization org = organizationRepository.findByOrganizationId(organizationId)
				.orElseThrow(() -> new RuntimeException("Organization not found"));
		return professionalTaxRepository.findByOrganization(org).stream().map(ProfessionalTaxMapper::toDTO)
				.collect(Collectors.toList());
	} */
	
	@Override
	@Transactional
	public ProfessionalTaxDTO resetToDefaultSlabs(String organizationId, String taxId) {

	    final String method = "resetToDefaultSlabs";
	    log.info("[{}] 🗑 Resetting PT slabs by deleting override | orgId={}, state={}",
	            method, organizationId, taxId);

	    // 1️⃣ Try to find override
	    OrgPTOverride override = orgPTOverrideRepository
	            .findByOrganizationIdAndState(organizationId, taxId)
	            .orElse(null);

	    if (override != null) {
	        orgPTOverrideRepository.delete(override);
	        log.info("[{}] 🗑 Override deleted successfully | overrideId={}", method, override.getId());
	    } else {
	        log.info("[{}] ℹ No override present → nothing to delete", method);
	    }

	    // 2️⃣ Now fetch fresh PT data (this will load master slabs)
	    List<ProfessionalTaxDTO> list = this.getAllProfessionalTaxes(organizationId);

	    ProfessionalTaxDTO result = list.stream()
	            .filter(x -> x.getState().equals(taxId))
	            .findFirst()
	            .orElseThrow(() -> new RuntimeException("State not found after reset"));

	    log.info("[{}] ✔ Returning default master slabs", method);

	    return result;
	}

	
	
//	@Override
//	public List<ProfessionalTaxDTO> getAllProfessionalTaxes(String organizationId) {
//
//	    final String method = "getAllProfessionalTaxes";
//
//	    log.info("[{}] 📥 Fetching PT slabs | orgId={}", method, organizationId);
//
//	    // 1. Load Master JSON
//	    MasterConfig master = masterConfigRepository
//	            .findByComponentName("slabDetails")
//	            .orElseThrow(() -> {
//	                log.error("[{}] ❌ Master PT config not found for component=slabDetails", method);
//	                return new RuntimeException("Master PT config not found");
//	            });
//
//	    log.info("[{}] ✅ Master PT config loaded successfully", method);
//
//	    ObjectNode masterJson = (ObjectNode) master.getConfigData();
//
//	    // Convert master to final map
//	    Map<String, JsonNode> finalMap = new LinkedHashMap<>();
//	    masterJson.fields().forEachRemaining(e -> finalMap.put(e.getKey(), e.getValue()));
//
//	    log.info("[{}] 🗂 Total master states loaded = {}", method, finalMap.size());
//
//	    // Create effectiveFrom map for overrides
//	    Map<String, String> effectiveMap = new HashMap<>();
//
//	    // 2. Apply overrides
//	    List<OrgPTOverride> overrides = orgPTOverrideRepository.findByOrganizationId(organizationId);
//
//	    if (overrides.isEmpty()) {
//	        log.warn("[{}] ⚠ No overrides found for organizationId={}", method, organizationId);
//	    } else {
//	        log.info("[{}] 🔄 Applying overrides | count={}", method, overrides.size());
//	    }
//
//	    for (OrgPTOverride o : overrides) {
//	        log.info("[{}] 🔁 Override applied for state={} | effectiveFrom={}",
//	                 method, o.getState(), o.getEffectiveFrom());
//
//	        finalMap.put(o.getState(), o.getOverrideJson());
//	        effectiveMap.put(o.getState(),o.getEffectiveFrom() != null ? o.getEffectiveFrom().toString() : null);
//	    }
//
//	    // 3. Convert each state → DTO
//	    List<ProfessionalTaxDTO> response = new ArrayList<>();
//
//	    for (Map.Entry<String, JsonNode> entry : finalMap.entrySet()) {
//
//	        String state = entry.getKey();
//	        ArrayNode slabsJson = (ArrayNode) entry.getValue();
//
//	        log.info("[{}] ➡ Converting PT slab for state={} | totalSlabs={}",
//	                 method, state, slabsJson.size());
//
//	        ProfessionalTaxDTO dto = new ProfessionalTaxDTO();
//	        dto.setState(state);
//	        dto.setTaxId(state);
//
//	        // 🔥 Set effectiveFrom from override (null for master)
//	        dto.setEffectiveFrom(effectiveMap.get(state));
//
//	        dto.setSlabDetails(convertSlabDetails(slabsJson));
//	        dto.setSlabRateConfigurations(new ArrayList<>()); // currently empty
//
//	        response.add(dto);
//	    }
//
//	    log.info("[{}] 📤 PT slabs prepared successfully | totalStates={}",
//	             method, response.size());
//
//	    return response;
//	}



	@Override
	public List<ProfessionalTaxDTO> getAllProfessionalTaxes(String organizationId) {

		final String method = "getAllProfessionalTaxes";

		log.info("[{}] 📥 Fetching PT slabs | orgId={}", method, organizationId);

		// 0️⃣ Load Organization and derive allowed states
		Organization organization = organizationRepository
				.findByOrganizationId(organizationId)
				.orElseThrow(() -> {
					log.error("[{}] ❌ Organization not found | orgId={}", method, organizationId);
					return new RuntimeException("Organization not found for id: " + organizationId);
				});

		// 🔎 Collect states from work locations (only active ones)
		Set<String> allowedStates = new HashSet<>();

		if (organization.getWorkLocations() != null && !organization.getWorkLocations().isEmpty()) {
			organization.getWorkLocations().stream()
					.filter(wl -> wl.getStatus() == null || Boolean.TRUE.equals(wl.getStatus())) // active or null
					.map(WorkLocation::getState)
					.filter(Objects::nonNull)
					.forEach(allowedStates::add);
		}

		// If no active workLocation states, fall back to organization's own state
		if (allowedStates.isEmpty() && organization.getState() != null) {
			allowedStates.add(organization.getState());
			log.info("[{}] 📍 No active workLocation states found, falling back to organization.state={}",
					method, organization.getState());
		}

		log.info("[{}] ✅ Allowed states for orgId={} => {}", method, organizationId, allowedStates);

		// If still empty, we cannot filter by state – just return empty list (or decide your own behavior)
		if (allowedStates.isEmpty()) {
			log.warn("[{}] ⚠ No state found for organization (neither workLocations nor org.state). Returning empty PT list.",
					method);
			return Collections.emptyList();
		}

		// 1️⃣ Load Master JSON
		MasterConfig master = masterConfigRepository
				.findByComponentName("slabDetails")
				.orElseThrow(() -> {
					log.error("[{}] ❌ Master PT config not found for component=slabDetails", method);
					return new RuntimeException("Master PT config not found");
				});

		log.info("[{}] ✅ Master PT config loaded successfully", method);

		ObjectNode masterJson = (ObjectNode) master.getConfigData();

		// Convert master to final map
		Map<String, JsonNode> finalMap = new LinkedHashMap<>();
		masterJson.fields().forEachRemaining(e -> finalMap.put(e.getKey(), e.getValue()));

		log.info("[{}] 🗂 Total master states loaded = {}", method, finalMap.size());

		// Create effectiveFrom map for overrides
		Map<String, String> effectiveMap = new HashMap<>();

		// 2️⃣ Apply overrides
		List<OrgPTOverride> overrides = orgPTOverrideRepository.findByOrganizationId(organizationId);

// 🔥 Create map for quick access (important)
		Map<String, OrgPTOverride> overrideMap = new HashMap<>();

		if (overrides.isEmpty()) {
			log.warn("[{}] ⚠ No overrides found for organizationId={}", method, organizationId);
		} else {
			log.info("[{}] 🔄 Applying overrides | count={}", method, overrides.size());
		}

		for (OrgPTOverride o : overrides) {

			overrideMap.put(o.getState(), o);   // 🔥 store full object

			log.info("[{}] 🔁 Override applied for state={} | effectiveFrom={}",
					method, o.getState(), o.getEffectiveFrom());

			finalMap.put(o.getState(), o.getOverrideJson());

			effectiveMap.put(o.getState(),
					o.getEffectiveFrom() != null ? o.getEffectiveFrom().toString() : null);
		}


		// 3️⃣ Convert each state → DTO (filtered by allowedStates)
		List<ProfessionalTaxDTO> response = new ArrayList<>();

		for (Map.Entry<String, JsonNode> entry : finalMap.entrySet()) {

			String state = entry.getKey();

			// 🚫 Skip states that are not in the org’s allowed states
			if (!allowedStates.contains(state)) {
				log.debug("[{}] ⏭ Skipping PT slab for state={} as it is not in organization's states {}",
						method, state, allowedStates);
				continue;
			}

			ArrayNode slabsJson = (ArrayNode) entry.getValue();

			log.info("[{}] ➡ Converting PT slab for state={} | totalSlabs={}",
					method, state, slabsJson.size());

			ProfessionalTaxDTO dto = new ProfessionalTaxDTO();
			dto.setState(state);
			dto.setTaxId(state);

// 🔥 Set effectiveFrom from override (null for master)
			dto.setEffectiveFrom(effectiveMap.get(state));

// 🔥 NEW: Set registration number from override
			OrgPTOverride stateOverride = overrideMap.get(state);
			if (stateOverride != null) {
				dto.setRegistrationNumber(stateOverride.getRegistrationNumber());
			}

			dto.setSlabDetails(convertSlabDetails(slabsJson));
			dto.setSlabRateConfigurations(new ArrayList<>()); // currently empty

			response.add(dto);
		}

		log.info("[{}] 📤 PT slabs prepared successfully | totalStates={} (after filtering by org workLocations)",
				method, response.size());

		return response;
	}



	private List<SlabDetailDTO> convertSlabDetails(ArrayNode array) {

	    final String method = "convertSlabDetails";

	    List<SlabDetailDTO> list = new ArrayList<>();

	    log.info("[{}] 🔍 Converting {} slab entries", method, array.size());

	    for (JsonNode node : array) {

	        SlabDetailDTO dto = new SlabDetailDTO();

	        dto.setId(null); // JSON does not have ID
	        dto.setStartAmount(node.get("startAmount").asDouble());
	        dto.setEndAmount(node.get("endAmount").asDouble());
	        dto.setPayAmount(node.get("payAmount").asDouble());
	        dto.setFemaleExempted(node.get("isFemaleExempted").asBoolean());

	        // deductionMonths
	        List<String> months = new ArrayList<>();
	        if (node.has("deductionMonths") && node.get("deductionMonths").isArray()) {
	            node.get("deductionMonths").forEach(m -> months.add(m.asText()));
	        }
	        dto.setDeductionMonths(months);

	        // JSON does not provide this → default false
	        dto.setDefaultFromMaster(false);

	        list.add(dto);
	    }

	    log.info("[{}] ✅ Completed conversion of {}", method, list.size());

	    return list;
	}



}