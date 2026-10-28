/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
import {
  RepresentationLoadingIndicator,
  Selection,
  useSelection,
  WorkbenchViewComponentProps,
  WorkbenchViewHandle,
} from "@eclipse-sirius/sirius-components-core";
import {
  FormBasedView,
  FormContext,
  GQLForm,
  Group,
} from "@eclipse-sirius/sirius-components-forms";
import DescriptionIcon from "@mui/icons-material/Description";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { forwardRef, useEffect, useImperativeHandle, useState } from "react";
import { useDocumentationSubscription } from "./useDocumentationSubscription";

const DocumentationContent = ({
  editingContextId,
  objectIds,
  readOnly,
}: {
  editingContextId: string;
  objectIds: string[];
  readOnly: boolean;
}) => {
  const { payload, loading, complete } = useDocumentationSubscription(
    editingContextId,
    objectIds
  );
  const [form, setForm] = useState<GQLForm | null>(null);
  const [canEdit, setCanEdit] = useState(false);
  useEffect(() => {
    if (payload?.__typename === "FormRefreshedEventPayload" && payload.form)
      setForm(payload.form);
    if (
      payload?.__typename === "FormCapabilitiesRefreshedEventPayload" &&
      payload.capabilities
    )
      setCanEdit(payload.capabilities.canEdit);
  }, [payload]);
  if (complete) return null;
  if (!form || loading) return <RepresentationLoadingIndicator />;
  return (
    <FormContext.Provider value={{ payload }}>
      <FormBasedView
        editingContextId={editingContextId}
        form={form}
        initialSelectedPageLabel={null}
        readOnly={readOnly || !canEdit}
        postProcessor={(contextId, convertedForm, isReadOnly) => {
          const group = convertedForm.pages[0]?.groups[0];
          return group ? (
            <Group
              editingContextId={contextId}
              formId={convertedForm.id}
              readOnly={isReadOnly}
              group={group}
            />
          ) : (
            <></>
          );
        }}
      />
    </FormContext.Provider>
  );
};

export const DocumentationView = forwardRef<
  WorkbenchViewHandle,
  WorkbenchViewComponentProps
>(({ id, editingContextId, readOnly }, ref) => {
  const { selection } = useSelection();
  const [objectIds, setObjectIds] = useState<string[]>([]);
  const applySelection = (next: Selection) =>
    setObjectIds(next.entries.map((entry) => entry.id));
  useEffect(() => {
    applySelection(selection);
  }, [selection]);
  useImperativeHandle(
    ref,
    () => ({ id, applySelection, getWorkbenchViewConfiguration: () => ({}) }),
    [id]
  );
  return (
    <Box
      sx={{ display: "flex", flexDirection: "column" }}
      data-testid="view-Documentation"
    >
      <Box
        sx={(theme) => ({
          display: "flex",
          alignItems: "center",
          borderBottom: `1px solid ${theme.palette.divider}`,
        })}
      >
        <DescriptionIcon sx={(theme) => ({ margin: theme.spacing(1) })} />
        <Typography>Documentation</Typography>
      </Box>
      <Box sx={{ flexGrow: 1, minHeight: 0, overflow: "auto" }}>
        {objectIds.length ? (
          <DocumentationContent
            key={JSON.stringify(objectIds)}
            editingContextId={editingContextId}
            objectIds={objectIds}
            readOnly={readOnly}
          />
        ) : (
          <span>Select an element</span>
        )}
      </Box>
    </Box>
  );
});
