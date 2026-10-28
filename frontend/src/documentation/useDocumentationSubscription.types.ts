/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
import {
  GQLForm,
  GQLFormEventPayload,
} from "@eclipse-sirius/sirius-components-forms";

export interface GQLDocumentationVariables {
  input: { id: string; editingContextId: string; formId: string };
}

export interface GQLDocumentationData {
  formEvent: GQLFormEventPayload & {
    form?: GQLForm;
    capabilities?: { canEdit: boolean };
  };
}
