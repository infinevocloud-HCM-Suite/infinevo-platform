/**
 * Validation rules for the Apply Reimbursement form.
 * Returns an object with field errors. Empty object means valid.
 */
export function validateReimbursementForm(formData) {
  const errors = {};

  // Reimbursement Type
  if (formData.isOtherType) {
    if (!formData.customType || formData.customType.trim() === "") {
      errors.reimbursementType = "Please enter the reimbursement type.";
    }
  } else {
    if (!formData.reimbursementType || formData.reimbursementType.trim() === "") {
      errors.reimbursementType = "Reimbursement type is required.";
    }
  }

  // Requested Amount
  if (
    formData.requestedAmount === "" ||
    formData.requestedAmount === null ||
    formData.requestedAmount === undefined
  ) {
    errors.requestedAmount = "Amount is required.";
  } else if (isNaN(Number(formData.requestedAmount))) {
    errors.requestedAmount = "Amount must be a valid number.";
  } else if (Number(formData.requestedAmount) <= 0) {
    errors.requestedAmount = "Amount must be greater than 0.";
  }

  // Bill Date
  if (!formData.billDate) {
    errors.billDate = "Bill date is required.";
  } else {
    const selected = new Date(formData.billDate);
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    if (selected > today) {
      errors.billDate = "Bill date cannot be a future date.";
    }
  }

  // Description
  if (!formData.description || formData.description.trim() === "") {
    errors.description = "Description is required.";
  }

  // Attachments (optional field, but validate type/size for each file if provided)
  const files = Array.isArray(formData.attachments)
    ? formData.attachments
    : formData.attachments
    ? [formData.attachments]
    : Array.isArray(formData.attachment)
    ? formData.attachment
    : formData.attachment
    ? [formData.attachment]
    : [];

  if (files.length > 0) {
    const allowedExtensions = [".pdf", ".jpg", ".jpeg", ".png"];
    const allowedMimeTypes = [
      "application/pdf",
      "image/jpeg",
      "image/png",
      "image/jpg",
    ];
    const maxSizeBytes = 5 * 1024 * 1024; // 5 MB per file

    for (const file of files) {
      if (!file) continue;
      const fileName = (file.name || "").toLowerCase();
      const hasValidExt = allowedExtensions.some((ext) => fileName.endsWith(ext));
      const hasValidType = allowedMimeTypes.includes(file.type);

      if (!hasValidExt && !hasValidType) {
        errors.attachment = `"${file.name}" is not supported. Only PDF, JPG, and PNG files are allowed.`;
        break;
      } else if (file.size > maxSizeBytes) {
        errors.attachment = `"${file.name}" exceeds 5 MB. Each file must be under 5 MB.`;
        break;
      }
    }
  }

  return errors;
}

export default validateReimbursementForm;
