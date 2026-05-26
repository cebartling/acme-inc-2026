import { By, PageElement } from '@serenity-js/web';

export const SearchPage = {
  searchInput: () =>
    PageElement.located(By.css('[data-testid="searchInput"]'))
      .describedAs('product search input'),

  submitButton: () =>
    PageElement.located(By.css('[data-testid="searchSubmitButton"]'))
      .describedAs('search submit button'),

  clearButton: () =>
    PageElement.located(By.css('[data-testid="searchClearButton"]'))
      .describedAs('search clear button'),

  resultCount: () =>
    PageElement.located(By.css('[data-testid="searchResultCount"]'))
      .describedAs('search result count'),

  resultsGrid: () =>
    PageElement.located(By.css('[data-testid="searchResultsGrid"]'))
      .describedAs('search results grid'),

  resultCard: () =>
    PageElement.located(By.css('[data-testid="searchResultCard"]'))
      .describedAs('product result card'),

  emptyState: () =>
    PageElement.located(By.css('[data-testid="searchEmptyState"]'))
      .describedAs('no results empty state'),

  spellingSuggestion: () =>
    PageElement.located(By.css('[data-testid="searchSpellingSuggestion"]'))
      .describedAs('spelling suggestion button'),

  sortSelector: () =>
    PageElement.located(By.css('[data-testid="searchSortSelector"]'))
      .describedAs('sort results dropdown'),

  pagination: () =>
    PageElement.located(By.css('[data-testid="searchPagination"]'))
      .describedAs('search pagination'),

  previousPageButton: () =>
    PageElement.located(By.css('[aria-label="Previous page"]'))
      .describedAs('previous page button'),

  nextPageButton: () =>
    PageElement.located(By.css('[aria-label="Next page"]'))
      .describedAs('next page button'),

  autocompleteDropdown: () =>
    PageElement.located(By.css('[data-testid="autocomplete-dropdown"]'))
      .describedAs('autocomplete suggestions dropdown'),

  autocompleteProductItem: () =>
    PageElement.located(By.css('[data-testid="autocomplete-product-item"]'))
      .describedAs('autocomplete product suggestion'),

  autocompleteCategoryItem: () =>
    PageElement.located(By.css('[data-testid="autocomplete-category-item"]'))
      .describedAs('autocomplete category suggestion'),

  autocompleteQueryItem: () =>
    PageElement.located(By.css('[data-testid="autocomplete-query-item"]'))
      .describedAs('autocomplete recent search suggestion'),
};
