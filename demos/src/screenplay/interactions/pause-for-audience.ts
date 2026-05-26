import { Duration, Interaction, the, Wait } from '@serenity-js/core';

export const PauseForAudience = (milliseconds: number) =>
  Wait.for(Duration.ofMilliseconds(milliseconds));

export const Pause = {
  forAudience: (milliseconds: number) =>
    Interaction.where(
      the`#actor pauses for ${milliseconds}ms for the audience`,
      async () => {
        await new Promise((resolve) => setTimeout(resolve, milliseconds));
      }
    ),
};
