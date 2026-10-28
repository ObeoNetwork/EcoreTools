/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
import { gql, OnDataOptions, useSubscription } from "@apollo/client";
import { useMultiToast } from "@eclipse-sirius/sirius-components-core";
import {
  formCapabilitiesRefreshedEventPayloadFragment,
  formRefreshedEventPayloadFragment,
} from "@eclipse-sirius/sirius-components-forms";
import { useState } from "react";
import { flushSync } from "react-dom";
import {
  GQLDocumentationData,
  GQLDocumentationVariables,
} from "./useDocumentationSubscription.types";

const subscription = gql`
  subscription documentationEvent($input: FormEventInput!) {
    formEvent(input: $input) {
      __typename
      ... on FormRefreshedEventPayload {
        ...formRefreshedEventPayloadFragment
      }
      ... on FormCapabilitiesRefreshedEventPayload {
        ...formCapabilitiesRefreshedEventPayloadFragment
      }
    }
  }
  ${formRefreshedEventPayloadFragment}
  ${formCapabilitiesRefreshedEventPayloadFragment}
`;

export const useDocumentationSubscription = (
  editingContextId: string,
  objectIds: string[]
) => {
  const [id] = useState(() => crypto.randomUUID());
  const [payload, setPayload] = useState<
    GQLDocumentationData["formEvent"] | null
  >(null);
  const [complete, setComplete] = useState(false);
  const { addErrorMessage } = useMultiToast();
  const variables: GQLDocumentationVariables = {
    input: {
      id,
      editingContextId,
      formId: `documentation://?objectIds=[${objectIds
        .map(encodeURIComponent)
        .join(",")}]`,
    },
  };
  const { loading } = useSubscription<
    GQLDocumentationData,
    GQLDocumentationVariables
  >(subscription, {
    variables,
    fetchPolicy: "no-cache",
    onData: ({ data }: OnDataOptions<GQLDocumentationData>) => {
      if (data.data)
        flushSync(() => {
          setPayload(data.data!.formEvent);
          setComplete(false);
        });
    },
    onComplete: () => setComplete(true),
    onError: ({ message }) => addErrorMessage(message),
  });
  return { payload, loading, complete };
};
